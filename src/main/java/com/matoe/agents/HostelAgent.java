package com.matoe.agents;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matoe.domain.AccommodationOption;
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

import java.util.*;
import java.util.stream.Collectors;

/**
 * Hostel / budget accommodation search agent — searches Hostelworld, Booking.com
 * via browser-use, falls back to LLM-generated results when browser service is unavailable.
 * Results are tagged with source provenance ("browser" or "llm").
 */
@Component
public class HostelAgent {

    private static final Logger log = LoggerFactory.getLogger(HostelAgent.class);

    private final BrowserAgentService browserService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;
    private final DynamicPromptService dynamicPromptService;
    private final LlmCostTrackingService costTracker;
    private final SearchTargetService searchTargetService;

    @Value("${travel-agency.prompts.hostel-agent}")
    private String defaultPrompt;

    @Value("${travel-agency.browser.hostel-sites:hostelworld.com,booking.com}")
    private String hostelSites;

    public HostelAgent(BrowserAgentService browserService, LlmService llmService,
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
        // Register YAML default (DB version will override if set by admin)
        dynamicPromptService.registerDefault("hostel-agent", "");
    }

    @jakarta.annotation.PostConstruct
    void init() {
        dynamicPromptService.registerDefault("hostel-agent", defaultPrompt);
    }

    public List<AccommodationOption> searchHostels(TravelRequest request) {
        long nights = request.nights();
        String model = request.extractorModel();

        // ── primary: real browser search ─────────────────────────────────────
        if (browserService.isAvailable()) {
            try {
                List<Map<String, Object>> raw = browserService.browseForList(
                    buildBrowserTask(request, nights),
                    searchTargetService.getSites("hostel-agent", hostelSites),
                    "a JSON array of hostel objects each with: name, pricePerNight (number), " +
                    "totalPrice (number), rating (number 1-5), location (string), " +
                    "amenities (array of strings), bookingUrl (string)",
                    model
                );
                if (raw != null && !raw.isEmpty()) {
                    log.info("HostelAgent: {} results via browser for {}", raw.size(), request.destination());
                    return raw.stream().map(m -> map(m, nights, "browser")).collect(Collectors.toList());
                }
            } catch (Exception e) {
                log.warn("HostelAgent browser search failed, falling back to LLM: {}", e.getMessage());
            }
        }

        // ── fallback: LLM-generated results (marked as source=llm) ───────────
        try {
            String systemPrompt = dynamicPromptService.getPrompt("hostel-agent");
            if (systemPrompt.isBlank()) systemPrompt = defaultPrompt;
            String userPrompt = promptTemplateService.buildHostelPrompt(systemPrompt, request);

            long start = System.currentTimeMillis();
            String raw = llmService.call(model, "You are a travel search expert. Return ONLY valid JSON array.", userPrompt);
            long durationMs = System.currentTimeMillis() - start;

            costTracker.logCall(request.sessionId(), "hostel-agent", model != null ? model : "default",
                resolveProvider(model), estimateTokens(userPrompt), estimateTokens(raw), durationMs, true, null);

            List<Map<String, Object>> items = objectMapper.readValue(
                llmService.extractJson(raw), new TypeReference<>() {});
            return items.stream().map(m -> map(m, nights, "llm")).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("HostelAgent LLM fallback failed for {}: {}", request.destination(), e.getMessage());
            costTracker.logCall(request.sessionId(), "hostel-agent", request.extractorModel(),
                "unknown", 0, 0, 0, false, e.getMessage());
            return List.of();
        }
    }

    private String buildBrowserTask(TravelRequest request, long nights) {
        return String.format(
            "Find 3-5 hostels or budget accommodation in %s for %d guests, " +
            "check-in %s, check-out %s (%d nights). " +
            "Budget: \u20ac%.0f\u2013\u20ac%.0f total. Travel style: %s. " +
            "For each get: name, price per night, total price, rating, neighborhood, amenities, booking URL.",
            request.destination(), request.guestCount(),
            request.startDate(), request.endDate(), nights,
            request.budgetMin(), request.budgetMax(), request.travelStyle()
        );
    }

    private AccommodationOption map(Map<String, Object> m, long nights, String source) {
        double ppn  = num(m, "pricePerNight");
        double tot  = num(m, "totalPrice", ppn * nights);
        double avgPpn = nights > 0 ? tot / nights : ppn;
        String tier = avgPpn < 30 ? "budget" : avgPpn > 80 ? "luxury" : "standard";
        return new AccommodationOption(
            UUID.randomUUID().toString(), "hostel", str(m, "name"), ppn, tot,
            num(m, "rating"), str(m, "location"), toStringList(m.get("amenities")),
            str(m, "bookingUrl"), tier, source, str(m, "imageUrl")
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
