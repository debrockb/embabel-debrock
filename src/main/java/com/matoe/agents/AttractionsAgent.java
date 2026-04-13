package com.matoe.agents;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matoe.domain.AttractionOption;
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

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Attractions search agent — finds activities, tours, and experiences.
 * Primary: browser-use visits Viator, GetYourGuide, TripAdvisor.
 * Fallback: LLM-generated attraction results with cost tracking.
 */
@Component
public class AttractionsAgent {

    private static final Logger log = LoggerFactory.getLogger(AttractionsAgent.class);

    private final BrowserAgentService browserService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;
    private final DynamicPromptService dynamicPromptService;
    private final LlmCostTrackingService costTracker;
    private final SearchTargetService searchTargetService;

    @Value("${travel-agency.prompts.attractions-agent}")
    private String defaultPrompt;

    @Value("${travel-agency.browser.attraction-sites:viator.com,getyourguide.com,tripadvisor.com}")
    private String attractionSites;

    public AttractionsAgent(BrowserAgentService browserService, LlmService llmService,
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

    @PostConstruct
    void init() {
        dynamicPromptService.registerDefault("attractions-agent", defaultPrompt);
    }

    public List<AttractionOption> searchAttractions(TravelRequest request) {
        String model = request.extractorModel();
        String interestTags = request.interestTags() != null && !request.interestTags().isEmpty()
            ? String.join(", ", request.interestTags())
            : "general sightseeing";

        // ── primary: real browser search ─────────────────────────────────────
        if (browserService.isAvailable()) {
            try {
                List<Map<String, Object>> raw = browserService.browseForList(
                    buildBrowserTask(request, interestTags),
                    searchTargetService.getSites("attractions-agent", attractionSites),
                    "a JSON array of attraction objects each with: name, description, category, " +
                    "price (number in USD), duration (string like '2h' or 'half-day'), " +
                    "rating (number 1-5), location (string), bookingUrl (string), tags (array of strings), " +
                    "latitude (number, decimal degrees), longitude (number, decimal degrees)",
                    model
                );
                if (raw != null && !raw.isEmpty()) {
                    log.info("AttractionsAgent: {} results via browser for {}", raw.size(), request.destination());
                    return raw.stream().map(m -> mapAttraction(m, "browser")).collect(Collectors.toList());
                }
            } catch (Exception e) {
                log.warn("AttractionsAgent browser search failed, falling back to LLM: {}", e.getMessage());
            }
        }

        // ── fallback: LLM-generated results ─────────────────────────────────
        try {
            String systemPrompt = dynamicPromptService.getPrompt("attractions-agent");
            if (systemPrompt.isBlank()) systemPrompt = defaultPrompt;

            String userPrompt = String.format(
                "Find 6-8 attractions, tours, and activities in %s for %d guests " +
                "visiting from %s to %s. Budget: $%.0f-$%.0f total trip. " +
                "Interests: %s. Travel style: %s. " +
                "Return a JSON array where each object has: name, description, category, " +
                "price (number in USD per person), duration (string), rating (number 1-5), " +
                "location (string), bookingUrl (string), tags (array of strings), " +
                "latitude (decimal degrees), longitude (decimal degrees). " +
                "Return ONLY valid JSON array.",
                request.destination(), request.guestCount(),
                request.startDate(), request.endDate(),
                request.budgetMin(), request.budgetMax(),
                interestTags, request.travelStyle()
            );

            long start = System.currentTimeMillis();
            String raw = llmService.call(model, systemPrompt, userPrompt);
            long durationMs = System.currentTimeMillis() - start;

            costTracker.logCall(request.sessionId(), "attractions-agent", model != null ? model : "default",
                resolveProvider(model), estimateTokens(userPrompt), estimateTokens(raw), durationMs, true, null);

            List<Map<String, Object>> items = objectMapper.readValue(
                llmService.extractJson(raw), new TypeReference<>() {});
            return items.stream().map(m -> mapAttraction(m, "llm")).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("AttractionsAgent LLM fallback failed for {}: {}", request.destination(), e.getMessage());
            costTracker.logCall(request.sessionId(), "attractions-agent", request.extractorModel(),
                "unknown", 0, 0, 0, false, e.getMessage());
            return List.of();
        }
    }

    private String buildBrowserTask(TravelRequest request, String interestTags) {
        return String.format(
            "Search for 6-8 attractions, tours, and activities in %s for %d guests, " +
            "visiting from %s to %s. Budget: $%.0f-$%.0f total. Travel style: %s. " +
            "Interests: %s. " +
            "For each attraction get: name, description, category, price per person, duration, " +
            "rating, location/neighborhood, booking URL, relevant tags, " +
            "and approximate GPS coordinates (latitude, longitude as decimal numbers).",
            request.destination(), request.guestCount(),
            request.startDate(), request.endDate(),
            request.budgetMin(), request.budgetMax(), request.travelStyle(),
            interestTags
        );
    }

    private AttractionOption mapAttraction(Map<String, Object> m, String source) {
        double price = num(m, "price");
        String tier = price < 30 ? "budget" : price > 100 ? "luxury" : "standard";
        return new AttractionOption(
            UUID.randomUUID().toString(),
            str(m, "name"),
            str(m, "description"),
            str(m, "category"),
            price,
            str(m, "duration"),
            num(m, "rating"),
            str(m, "location"),
            str(m, "bookingUrl"),
            tier,
            source,
            toStringList(m.get("tags")),
            num(m, "latitude"),
            num(m, "longitude")
        );
    }

    private double num(Map<String, Object> m, String key) { return num(m, key, 0.0); }
    private double num(Map<String, Object> m, String key, double def) {
        Object v = m.get(key); return v instanceof Number n ? n.doubleValue() : def; }
    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key); return v != null ? v.toString() : ""; }
    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object v) {
        if (v instanceof List<?> l) return l.stream().map(Object::toString).collect(Collectors.toList());
        return List.of(); }
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
