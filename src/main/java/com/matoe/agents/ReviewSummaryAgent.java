package com.matoe.agents;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * Review Summary Agent — synthesizes traveler review sentiment for the destination.
 * LLM-only agent (no browser scraping needed).
 * Returns structured summary of traveler opinions, pros/cons, and suitability ratings.
 */
@Component
public class ReviewSummaryAgent {

    private static final Logger log = LoggerFactory.getLogger(ReviewSummaryAgent.class);

    private final BrowserAgentService browserService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;
    private final DynamicPromptService dynamicPromptService;
    private final LlmCostTrackingService costTracker;

    @Value("${travel-agency.prompts.review-summary-agent}")
    private String defaultPrompt;

    public ReviewSummaryAgent(BrowserAgentService browserService, LlmService llmService,
                              ObjectMapper objectMapper, PromptTemplateService promptTemplateService,
                              DynamicPromptService dynamicPromptService, LlmCostTrackingService costTracker) {
        this.browserService = browserService;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        this.promptTemplateService = promptTemplateService;
        this.dynamicPromptService = dynamicPromptService;
        this.costTracker = costTracker;
    }

    @PostConstruct
    void init() {
        dynamicPromptService.registerDefault("review-summary-agent", defaultPrompt);
    }

    public Map<String, Object> getReviewSummary(TravelRequest request) {
        String model = request.extractorModel();

        try {
            String systemPrompt = dynamicPromptService.getPrompt("review-summary-agent");
            if (systemPrompt.isBlank()) systemPrompt = defaultPrompt;

            String userPrompt = String.format(
                "Synthesize a traveler review summary for %s, considering the travel period %s to %s " +
                "and travel style '%s'. " +
                "Return a JSON object with keys: " +
                "overallSentiment (string, e.g. 'Very Positive', 'Positive', 'Mixed', 'Negative'), " +
                "topPros (array of strings, 3-5 most praised aspects), " +
                "topCons (array of strings, 3-5 most common complaints), " +
                "bestFor (array of strings, traveler types this destination suits best), " +
                "avoidIf (array of strings, reasons someone might want to skip this destination), " +
                "safetyRating (string, e.g. 'Very Safe', 'Generally Safe', 'Exercise Caution'), " +
                "cleanlinessRating (string, e.g. 'Excellent', 'Good', 'Average', 'Below Average'). " +
                "Return ONLY valid JSON.",
                request.destination(), request.startDate(), request.endDate(),
                request.travelStyle()
            );

            long start = System.currentTimeMillis();
            String raw = llmService.call(model, systemPrompt, userPrompt);
            long durationMs = System.currentTimeMillis() - start;

            costTracker.logCall(request.sessionId(), "review-summary-agent", model != null ? model : "default",
                resolveProvider(model), estimateTokens(userPrompt), estimateTokens(raw), durationMs, true, null);

            return objectMapper.readValue(llmService.extractJson(raw), new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("ReviewSummaryAgent LLM call failed for {}: {}", request.destination(), e.getMessage());
            costTracker.logCall(request.sessionId(), "review-summary-agent", request.extractorModel(),
                "unknown", 0, 0, 0, false, e.getMessage());
            return defaultReviewSummary(request.destination());
        }
    }

    private Map<String, Object> defaultReviewSummary(String destination) {
        Map<String, Object> d = new HashMap<>();
        d.put("overallSentiment", "Positive");
        d.put("topPros", List.of(
            "Rich cultural heritage and history",
            "Diverse food scene",
            "Friendly locals"
        ));
        d.put("topCons", List.of(
            "Can be crowded during peak season",
            "Tourist-area prices may be inflated",
            "Language barrier in some areas"
        ));
        d.put("bestFor", List.of(
            "Culture enthusiasts",
            "Food lovers",
            "History buffs"
        ));
        d.put("avoidIf", List.of(
            "You prefer off-the-beaten-path destinations",
            "You are on a very tight budget during peak season"
        ));
        d.put("safetyRating", "Generally Safe");
        d.put("cleanlinessRating", "Good");
        return d;
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
