package com.matoe.agents;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Country Specialist Agent — provides regional travel intelligence.
 * Primary: browser-use visits Lonely Planet, Wikivoyage, TripAdvisor.
 * Fallback: LLM-generated insights with cost tracking.
 */
@Component
public class CountrySpecialistAgent {

    private static final Logger log = LoggerFactory.getLogger(CountrySpecialistAgent.class);

    private final BrowserAgentService browserService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;
    private final DynamicPromptService dynamicPromptService;
    private final LlmCostTrackingService costTracker;

    @Value("${travel-agency.prompts.country-specialist}")
    private String defaultPrompt;

    @Value("${travel-agency.models.country-specialist:lmstudio/llama-3-8b}")
    private String defaultModel;

    @Value("${travel-agency.browser.country-sites:lonelyplanet.com,wikivoyage.org,tripadvisor.com}")
    private String countrySites;

    public CountrySpecialistAgent(BrowserAgentService browserService, LlmService llmService,
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
        dynamicPromptService.registerDefault("country-specialist", defaultPrompt);
    }

    public Map<String, Object> gatherRegionalInsights(String destination, String modelString) {
        if (modelString == null || modelString.isBlank()) modelString = defaultModel;

        // ── primary: browser-use visits real tourism resources ──────────────
        if (browserService.isAvailable()) {
            try {
                Map<String, Object> insights = browserService.browseForMap(
                    String.format("Research travel intelligence for %s. " +
                        "Find: best time to visit, local transit tips, cultural customs, " +
                        "safety advice, local currency, primary language(s), typical weather, " +
                        "local cuisine highlights, visa requirements, emergency contact numbers.",
                        destination),
                    Arrays.asList(countrySites.split(",")),
                    "JSON object with keys: bestTimeToVisit, localTransitRecommendation, " +
                    "culturalTips, safetyConsiderations, currency, language, weatherExpectation, " +
                    "localCuisine, visaRequirements, emergencyNumbers",
                    modelString
                );
                if (insights != null && !insights.isEmpty()) {
                    log.info("CountrySpecialist: real data from browser for {}", destination);
                    return insights;
                }
            } catch (Exception e) {
                log.warn("CountrySpecialist browser failed, using LLM: {}", e.getMessage());
            }
        }

        // ── fallback: LLM-generated insights ─────────────────────────────────
        try {
            String systemPrompt = dynamicPromptService.getPrompt("country-specialist");
            if (systemPrompt.isBlank()) systemPrompt = defaultPrompt;

            String userPrompt = "Provide travel intelligence for " + destination + " as a JSON object with keys: " +
                "bestTimeToVisit, localTransitRecommendation, culturalTips, safetyConsiderations, " +
                "currency, language, weatherExpectation, localCuisine, visaRequirements, emergencyNumbers. " +
                "Return ONLY valid JSON.";

            long start = System.currentTimeMillis();
            String raw = llmService.call(modelString, systemPrompt, userPrompt);
            long durationMs = System.currentTimeMillis() - start;

            costTracker.logCall(null, "country-specialist", modelString,
                resolveProvider(modelString), estimateTokens(userPrompt), estimateTokens(raw),
                durationMs, true, null);

            return objectMapper.readValue(llmService.extractJson(raw), new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("CountrySpecialist LLM fallback failed for {}: {}", destination, e.getMessage());
            costTracker.logCall(null, "country-specialist", modelString,
                "unknown", 0, 0, 0, false, e.getMessage());
            return defaultInsights(destination);
        }
    }

    private Map<String, Object> defaultInsights(String destination) {
        Map<String, Object> d = new HashMap<>();
        d.put("bestTimeToVisit", "Spring or autumn for pleasant weather");
        d.put("localTransitRecommendation", "Use public transport or rideshare apps");
        d.put("culturalTips", "Research local customs before arriving");
        d.put("safetyConsiderations", "Exercise standard travel precautions");
        d.put("currency", "Check the local currency and exchange rates");
        d.put("language", "English is widely spoken in tourist areas");
        d.put("weatherExpectation", "Check forecast closer to travel date");
        d.put("localCuisine", "Try local specialties at family-run restaurants");
        d.put("visaRequirements", "Check visa requirements for your nationality before travel");
        d.put("emergencyNumbers", "Research local emergency numbers (police, ambulance, fire)");
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
