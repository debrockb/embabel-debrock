package com.matoe.agents;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matoe.domain.TransportOption;
import com.matoe.domain.TravelRequest;
import com.matoe.service.BrowserAgentService;
import com.matoe.service.DynamicPromptService;
import com.matoe.service.LlmCostTrackingService;
import com.matoe.service.LlmService;
import com.matoe.service.PromptTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Train search agent — searches Trainline, Omio, Seat61 via browser-use,
 * falls back to LLM-generated results when browser service is unavailable.
 * Results are tagged with source provenance ("browser" or "llm").
 */
@Component
public class TrainAgent {

    private static final Logger log = LoggerFactory.getLogger(TrainAgent.class);

    private final BrowserAgentService browserService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;
    private final DynamicPromptService dynamicPromptService;
    private final LlmCostTrackingService costTracker;

    @Value("${travel-agency.prompts.train-agent}")
    private String defaultPrompt;

    @Value("${travel-agency.browser.train-sites:thetrainline.com,omio.com,seat61.com}")
    private String trainSites;

    public TrainAgent(BrowserAgentService browserService, LlmService llmService,
                      ObjectMapper objectMapper, PromptTemplateService promptTemplateService,
                      DynamicPromptService dynamicPromptService, LlmCostTrackingService costTracker) {
        this.browserService = browserService;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        this.promptTemplateService = promptTemplateService;
        this.dynamicPromptService = dynamicPromptService;
        this.costTracker = costTracker;
        // Register YAML default (DB version will override if set by admin)
        dynamicPromptService.registerDefault("train-agent", "");
    }

    @jakarta.annotation.PostConstruct
    void init() {
        dynamicPromptService.registerDefault("train-agent", defaultPrompt);
    }

    public List<TransportOption> searchTrains(TravelRequest request) {
        long days = ChronoUnit.DAYS.between(request.startDate(), request.endDate());
        if (days <= 0) days = 1;
        String model = request.extractorModel();

        // -- primary: real browser search --
        if (browserService.isAvailable()) {
            try {
                List<Map<String, Object>> raw = browserService.browseForList(
                    buildBrowserTask(request, days),
                    Arrays.asList(trainSites.split(",")),
                    "a JSON array of train route objects each with: operator (string), " +
                    "departureTime (HH:mm), arrivalTime (HH:mm), duration (string), " +
                    "stops (integer), price (per person, number), bookingUrl (string), " +
                    "class (string), origin (string), destination (string)",
                    model
                );
                if (raw != null && !raw.isEmpty()) {
                    log.info("TrainAgent: {} results via browser for {}", raw.size(), request.destination());
                    return raw.stream().map(m -> map(m, "browser", request)).collect(Collectors.toList());
                }
            } catch (Exception e) {
                log.warn("TrainAgent browser search failed, falling back to LLM: {}", e.getMessage());
            }
        }

        // -- fallback: LLM-generated results (marked as source=llm) --
        try {
            String systemPrompt = dynamicPromptService.getPrompt("train-agent");
            if (systemPrompt.isBlank()) systemPrompt = defaultPrompt;
            String userPrompt = promptTemplateService.buildCarBusPrompt(systemPrompt, request);

            long start = System.currentTimeMillis();
            String raw = llmService.call(model, "You are a travel search expert. Return ONLY valid JSON array.", userPrompt);
            long durationMs = System.currentTimeMillis() - start;

            costTracker.logCall(request.sessionId(), "train-agent", model != null ? model : "default",
                resolveProvider(model), estimateTokens(userPrompt), estimateTokens(raw), durationMs, true, null);

            List<Map<String, Object>> items = objectMapper.readValue(
                llmService.extractJson(raw), new TypeReference<>() {});
            return items.stream().map(m -> map(m, "llm", request)).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("TrainAgent LLM fallback failed for {}: {}", request.destination(), e.getMessage());
            costTracker.logCall(request.sessionId(), "train-agent", request.extractorModel(),
                "unknown", 0, 0, 0, false, e.getMessage());
            return List.of();
        }
    }

    private String buildBrowserTask(TravelRequest request, long days) {
        return String.format(
            "Search for 4-6 train routes for %d passengers from %s to %s, " +
            "departing %s. Trip duration: %d days. Max budget per person: approx. %.0f EUR. " +
            "Include both standard and first-class options. " +
            "For each train get: operator, departure time, arrival time, duration, stops, " +
            "price per person, booking URL, class.",
            request.guestCount(),
            request.originCity().isEmpty() ? "nearest major station" : request.originCity(),
            request.destination(),
            request.startDate(), days, request.budgetMax()
        );
    }

    private TransportOption map(Map<String, Object> m, String source, TravelRequest request) {
        double price = num(m, "price");
        String tier = price < 80 ? "budget" : price > 300 ? "luxury" : "standard";
        return new TransportOption(
            UUID.randomUUID().toString(), "train",
            str(m, "operator"), str(m, "departureTime"), str(m, "arrivalTime"),
            str(m, "duration"), price,
            ((Number) m.getOrDefault("stops", 0)).intValue(),
            str(m, "bookingUrl"), tier, source,
            str(m, "origin").isEmpty() ? request.originCity() : str(m, "origin"),
            str(m, "destination").isEmpty() ? request.destination() : str(m, "destination")
        );
    }

    private double num(Map<String, Object> m, String key) {
        Object v = m.get(key); return v instanceof Number n ? n.doubleValue() : 0.0;
    }
    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key); return v != null ? v.toString() : "";
    }
    private String resolveProvider(String model) {
        if (model == null) return "anthropic";
        if (model.startsWith("anthropic/")) return "anthropic";
        if (model.startsWith("lmstudio/")) return "lmstudio";
        if (model.startsWith("ollama/")) return "ollama";
        if (model.startsWith("openrouter/")) return "openrouter";
        return "unknown";
    }
    private int estimateTokens(String text) { return text != null ? text.length() / 4 : 0; }
}
