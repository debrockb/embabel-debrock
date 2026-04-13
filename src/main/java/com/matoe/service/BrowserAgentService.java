package com.matoe.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Java client for the Python browser-use service.
 * Dispatches browsing tasks to the browser pool (load-balanced across 3 instances).
 * Each call spins a real Chromium browser that navigates to target sites and extracts data.
 */
@Service
public class BrowserAgentService {

    private static final Logger log = LoggerFactory.getLogger(BrowserAgentService.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${browser-service.url:http://localhost:8001}")
    private String browserServiceUrl;

    @Value("${browser-service.timeout-seconds:180}")
    private int timeoutSeconds;

    @Value("${browser-service.max-steps:20}")
    private int maxSteps;

    @Value("${browser-service.enabled:true}")
    private boolean enabled;

    public BrowserAgentService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * Check if the browser service is available and healthy.
     */
    public boolean isAvailable() {
        if (!enabled) return false;
        try {
            Map<?, ?> health = webClient.get()
                .uri(browserServiceUrl + "/health")
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(5));
            return health != null && "ok".equals(health.get("status"));
        } catch (Exception e) {
            log.debug("Browser service unavailable: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Browse the web using a real browser to complete the given task.
     * Returns parsed JSON results (list of maps) or null on failure.
     *
     * @param task            Natural language task (what to search/extract)
     * @param sites           Seed URLs to start from
     * @param extractionHint  JSON schema hint for the expected output format
     * @param model           LLM model to use for browser reasoning
     */
    @Observed(name = "matoe.browser.browse", contextualName = "browser-browse-list",
             lowCardinalityKeyValues = {"component", "browser-service"})
    public List<Map<String, Object>> browseForList(
            String task, List<String> sites, String extractionHint, String model) {
        try {
            Map<String, Object> result = browse(task, sites, extractionHint, model);
            if (result == null || !(Boolean) result.get("success")) {
                log.warn("Browser task failed: {}", result != null ? result.get("error") : "null response");
                return null;
            }

            Object data = result.get("result");
            if (data instanceof List) {
                //noinspection unchecked
                return (List<Map<String, Object>>) data;
            }
            if (data instanceof String) {
                // Try to parse raw string as JSON array
                String raw = (String) data;
                return objectMapper.readValue(raw, new TypeReference<List<Map<String, Object>>>() {});
            }
            return null;
        } catch (Exception e) {
            log.warn("browseForList failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Browse for a single JSON object (Map), e.g. country insights.
     */
    @Observed(name = "matoe.browser.browse", contextualName = "browser-browse-map",
             lowCardinalityKeyValues = {"component", "browser-service"})
    public Map<String, Object> browseForMap(
            String task, List<String> sites, String extractionHint, String model) {
        try {
            Map<String, Object> result = browse(task, sites, extractionHint, model);
            if (result == null || !(Boolean) result.get("success")) {
                return null;
            }

            Object data = result.get("result");
            if (data instanceof Map) {
                //noinspection unchecked
                return (Map<String, Object>) data;
            }
            if (data instanceof String) {
                return objectMapper.readValue((String) data, new TypeReference<Map<String, Object>>() {});
            }
            return null;
        } catch (Exception e) {
            log.warn("browseForMap failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Submit multiple browsing tasks at once (parallel batch call).
     * All tasks run concurrently in the browser pool.
     */
    public List<Map<String, Object>> browseBatch(List<Map<String, Object>> requests) {
        try {
            //noinspection unchecked
            return webClient.post()
                .uri(browserServiceUrl + "/browse/batch")
                .header("Content-Type", "application/json")
                .bodyValue(requests)
                .retrieve()
                .bodyToMono(List.class)
                .block(Duration.ofSeconds((long) timeoutSeconds * requests.size()));
        } catch (Exception e) {
            log.warn("browseBatch failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ── internal ─────────────────────────────────────────────────────────────

    private Map<String, Object> browse(
            String task, List<String> sites, String extractionHint, String model) {

        Map<String, Object> body = Map.of(
            "task", task,
            "sites", sites != null ? sites : List.of(),
            "extraction_schema", extractionHint != null ? extractionHint : "",
            "model", model != null ? model : "",
            "max_steps", maxSteps,
            "timeout_seconds", timeoutSeconds
        );

        log.info("Dispatching browser task to {}: {}", browserServiceUrl, task.substring(0, Math.min(80, task.length())));

        //noinspection unchecked
        return webClient.post()
            .uri(browserServiceUrl + "/browse")
            .header("Content-Type", "application/json")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map.class)
            .block(Duration.ofSeconds(timeoutSeconds + 30));
    }
}
