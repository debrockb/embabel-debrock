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
import com.matoe.service.SearchTargetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Ground transport agent — handles car rental AND bus searches separately,
 * using the appropriate prompt for each. Results from both are merged.
 */
@Component
public class CarBusAgent {

    private static final Logger log = LoggerFactory.getLogger(CarBusAgent.class);

    private final BrowserAgentService browserService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;
    private final DynamicPromptService dynamicPromptService;
    private final LlmCostTrackingService costTracker;
    private final SearchTargetService searchTargetService;

    @Value("${travel-agency.prompts.car-agent}")
    private String carPrompt;

    @Value("${travel-agency.prompts.bus-agent}")
    private String busPrompt;

    @Value("${travel-agency.browser.car-sites:rentalcars.com,kayak.com/cars}")
    private String carSites;

    @Value("${travel-agency.browser.bus-sites:flixbus.com,busbud.com,rome2rio.com}")
    private String busSites;

    public CarBusAgent(BrowserAgentService browserService, LlmService llmService,
                       ObjectMapper objectMapper, PromptTemplateService promptTemplateService,
                       DynamicPromptService dynamicPromptService, LlmCostTrackingService costTracker,
                       SearchTargetService searchTargetService) {
        this.browserService = browserService;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        this.promptTemplateService = promptTemplateService;
        this.dynamicPromptService = dynamicPromptService;
        this.costTracker = costTracker;
        this.searchTargetService = searchTargetService;
    }

    @jakarta.annotation.PostConstruct
    void init() {
        dynamicPromptService.registerDefault("car-agent", carPrompt);
        dynamicPromptService.registerDefault("bus-agent", busPrompt);
    }

    public List<TransportOption> searchGroundTransport(TravelRequest request) {
        boolean wantCar = request.transportTypes().contains("car");
        boolean wantBus = request.transportTypes().contains("bus");
        String model = request.extractorModel();
        List<TransportOption> results = new ArrayList<>();

        // -- primary: real browser search --
        if (browserService.isAvailable()) {
            try {
                long days = ChronoUnit.DAYS.between(request.startDate(), request.endDate());
                if (days <= 0) days = 1;
                String sites = "";
                if (wantCar) sites += carSites;
                if (wantBus) sites += (sites.isEmpty() ? "" : ",") + busSites;

                List<Map<String, Object>> raw = browserService.browseForList(
                    buildBrowserTask(request, days, wantCar, wantBus),
                    searchTargetService.getSites("car-agent", sites),
                    "a JSON array of ground transport objects each with: type ('car' or 'bus'), " +
                    "provider (string), departureTime (HH:mm), arrivalTime (HH:mm), " +
                    "duration (string), price (total trip, number), bookingUrl (string), " +
                    "origin (string), destination (string)",
                    model
                );
                if (raw != null && !raw.isEmpty()) {
                    log.info("CarBusAgent: {} results via browser for {}", raw.size(), request.destination());
                    return raw.stream().map(m -> map(m, "browser", request)).collect(Collectors.toList());
                }
            } catch (Exception e) {
                log.warn("CarBusAgent browser search failed, falling back to LLM: {}", e.getMessage());
            }
        }

        // -- fallback: LLM-generated results, using the correct prompt per type --
        if (wantCar) {
            results.addAll(searchViaLlm(request, model, "car-agent", "car"));
        }
        if (wantBus) {
            results.addAll(searchViaLlm(request, model, "bus-agent", "bus"));
        }
        return results;
    }

    private List<TransportOption> searchViaLlm(TravelRequest request, String model,
                                                String promptName, String transportType) {
        try {
            String systemPrompt = dynamicPromptService.getPrompt(promptName);
            if (systemPrompt.isBlank()) systemPrompt = "car-agent".equals(promptName) ? carPrompt : busPrompt;
            String userPrompt = promptTemplateService.buildCarBusPrompt(systemPrompt, request);

            long start = System.currentTimeMillis();
            String raw = llmService.call(model, "You are a travel search expert. Return ONLY valid JSON array.", userPrompt);
            long durationMs = System.currentTimeMillis() - start;

            costTracker.logCall(request.sessionId(), transportType + "-agent", model != null ? model : "default",
                resolveProvider(model), estimateTokens(userPrompt), estimateTokens(raw), durationMs, true, null);

            List<Map<String, Object>> items = objectMapper.readValue(
                llmService.extractJson(raw), new TypeReference<>() {});
            return items.stream().map(m -> map(m, "llm", request)).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("CarBusAgent {} LLM fallback failed for {}: {}", transportType, request.destination(), e.getMessage());
            costTracker.logCall(request.sessionId(), transportType + "-agent", request.extractorModel(),
                "unknown", 0, 0, 0, false, e.getMessage());
            return List.of();
        }
    }

    private String buildBrowserTask(TravelRequest request, long days, boolean wantCar, boolean wantBus) {
        String types = "";
        if (wantCar && wantBus) types = "car rental and bus";
        else if (wantCar) types = "car rental";
        else types = "bus";

        return String.format(
            "Search for 3-4 %s options to/from %s for %d people, %s to %s (%d days). " +
            "Budget: approx. %.0f EUR max total. For each option get: type (car or bus), " +
            "provider, departure time, arrival time, duration, total price, booking URL.",
            types, request.destination(), request.guestCount(),
            request.startDate(), request.endDate(), days, request.budgetMax()
        );
    }

    private TransportOption map(Map<String, Object> m, String source, TravelRequest request) {
        // Accept multiple field names for price — car prompts use totalPrice/pricePerDay,
        // bus prompts use price.
        double price = num(m, "price");
        if (price <= 0) price = num(m, "totalPrice");
        if (price <= 0) price = num(m, "pricePerDay"); // single day = total

        String tier = price < 100 ? "budget" : price > 400 ? "luxury" : "standard";
        String type = str(m, "type");
        if (!type.equals("car") && !type.equals("bus")) type = "car";
        return new TransportOption(
            UUID.randomUUID().toString(), type,
            str(m, "provider"), str(m, "departureTime"), str(m, "arrivalTime"),
            str(m, "duration"), price, 0,
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
