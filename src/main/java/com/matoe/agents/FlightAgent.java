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

import java.util.*;
import java.util.stream.Collectors;

/**
 * Flight search agent — searches Skyscanner, Google Flights, Kayak via browser-use,
 * falls back to LLM-generated results when browser service is unavailable.
 * Results are tagged with source provenance ("browser" or "llm").
 */
@Component
public class FlightAgent {

    private static final Logger log = LoggerFactory.getLogger(FlightAgent.class);

    private final BrowserAgentService browserService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;
    private final DynamicPromptService dynamicPromptService;
    private final LlmCostTrackingService costTracker;

    @Value("${travel-agency.prompts.flight-agent}")
    private String defaultPrompt;

    @Value("${travel-agency.browser.flight-sites:skyscanner.com,google.com/flights,kayak.com}")
    private String flightSites;

    public FlightAgent(BrowserAgentService browserService, LlmService llmService,
                       ObjectMapper objectMapper, PromptTemplateService promptTemplateService,
                       DynamicPromptService dynamicPromptService, LlmCostTrackingService costTracker) {
        this.browserService = browserService;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        this.promptTemplateService = promptTemplateService;
        this.dynamicPromptService = dynamicPromptService;
        this.costTracker = costTracker;
        // Register YAML default (DB version will override if set by admin)
        dynamicPromptService.registerDefault("flight-agent", "");
    }

    @jakarta.annotation.PostConstruct
    void init() {
        dynamicPromptService.registerDefault("flight-agent", defaultPrompt);
    }

    public List<TransportOption> searchFlights(TravelRequest request) {
        String model = request.extractorModel();

        // -- primary: real browser search --
        if (browserService.isAvailable()) {
            try {
                List<Map<String, Object>> raw = browserService.browseForList(
                    buildBrowserTask(request),
                    Arrays.asList(flightSites.split(",")),
                    "a JSON array of flight objects each with: airline (string), departureTime (HH:mm), " +
                    "arrivalTime (HH:mm), duration (e.g. '2h 30m'), stops (integer), " +
                    "price (per person, number), bookingUrl (string), origin (string), destination (string)",
                    model
                );
                if (raw != null && !raw.isEmpty()) {
                    log.info("FlightAgent: {} results via browser for {}", raw.size(), request.destination());
                    return raw.stream().map(m -> map(m, "browser", request)).collect(Collectors.toList());
                }
            } catch (Exception e) {
                log.warn("FlightAgent browser search failed, falling back to LLM: {}", e.getMessage());
            }
        }

        // -- fallback: LLM-generated results (marked as source=llm) --
        try {
            String systemPrompt = dynamicPromptService.getPrompt("flight-agent");
            if (systemPrompt.isBlank()) systemPrompt = defaultPrompt;
            String userPrompt = promptTemplateService.buildFlightPrompt(systemPrompt, request);

            long start = System.currentTimeMillis();
            String raw = llmService.call(model, "You are a travel search expert. Return ONLY valid JSON array.", userPrompt);
            long durationMs = System.currentTimeMillis() - start;

            costTracker.logCall(request.sessionId(), "flight-agent", model != null ? model : "default",
                resolveProvider(model), estimateTokens(userPrompt), estimateTokens(raw), durationMs, true, null);

            List<Map<String, Object>> items = objectMapper.readValue(
                llmService.extractJson(raw), new TypeReference<>() {});
            return items.stream().map(m -> map(m, "llm", request)).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("FlightAgent LLM fallback failed for {}: {}", request.destination(), e.getMessage());
            costTracker.logCall(request.sessionId(), "flight-agent", request.extractorModel(),
                "unknown", 0, 0, 0, false, e.getMessage());
            return List.of();
        }
    }

    private String buildBrowserTask(TravelRequest request) {
        return String.format(
            "Search for 4-6 flights for %d passengers from %s to %s, " +
            "departing %s returning %s. Max budget per person: approx. %.0f EUR. " +
            "Include budget and premium options. " +
            "For each flight get: airline, departure time, arrival time, duration, stops, price per person, booking URL.",
            request.guestCount(),
            request.originCity().isEmpty() ? "nearest major airport" : request.originCity(),
            request.destination(),
            request.startDate(), request.endDate(), request.budgetMax()
        );
    }

    private TransportOption map(Map<String, Object> m, String source, TravelRequest request) {
        double price = num(m, "price");
        String tier = price < 150 ? "budget" : price > 500 ? "luxury" : "standard";
        return new TransportOption(
            UUID.randomUUID().toString(), "flight",
            str(m, "airline"), str(m, "departureTime"), str(m, "arrivalTime"),
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
