package com.matoe.agents;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matoe.annotations.Action;
import com.matoe.service.BrowserAgentService;
import com.matoe.service.LlmService;
import com.matoe.service.PromptTemplateService;
import com.matoe.domain.TravelRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * Country Specialist Agent — provides regional travel intelligence.
 * Primary: browser-use visits Lonely Planet, Wikivoyage, official tourism sites.
 * Fallback: LLM-generated insights.
 */
@Component
public class CountrySpecialistAgent {

    private static final Logger log = LoggerFactory.getLogger(CountrySpecialistAgent.class);

    private final BrowserAgentService browserService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;

    @Value("${travel-agency.prompts.country-specialist}")
    private String systemPrompt;

    @Value("${travel-agency.models.country-specialist:lmstudio/llama-3-8b}")
    private String defaultModel;

    public CountrySpecialistAgent(BrowserAgentService browserService, LlmService llmService,
                                   ObjectMapper objectMapper, PromptTemplateService promptTemplateService) {
        this.browserService = browserService;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        this.promptTemplateService = promptTemplateService;
    }

    @Action(name = "gather_regional_insights", preconditions = {"destination_provided"}, effects = {"regional_insights_available"})
    public Map<String, Object> gatherRegionalInsights(String destination, String modelString) {
        if (modelString == null || modelString.isBlank()) modelString = defaultModel;

        // ── primary: browser-use visits real tourism resources ──────────────
        if (browserService.isAvailable()) {
            try {
                Map<String, Object> insights = browserService.browseForMap(
                    String.format("Research travel intelligence for %s. " +
                        "Find: best time to visit, local transit tips, cultural customs, " +
                        "safety advice, local currency, primary language(s), typical weather, local cuisine highlights.",
                        destination),
                    List.of(
                        "https://www.lonelyplanet.com",
                        "https://en.wikivoyage.org",
                        "https://www.tripadvisor.com"
                    ),
                    "JSON object with keys: bestTimeToVisit, localTransitRecommendation, " +
                    "culturalTips, safetyConsiderations, currency, language, weatherExpectation, localCuisine",
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
            // Build a temporary TravelRequest-like object for template substitution
            String userPrompt = "Provide travel intelligence for " + destination + " as a JSON object with keys: " +
                "bestTimeToVisit, localTransitRecommendation, culturalTips, safetyConsiderations, " +
                "currency, language, weatherExpectation, localCuisine. Return ONLY valid JSON.";
            String raw = llmService.call(modelString, systemPrompt, userPrompt);
            return objectMapper.readValue(llmService.extractJson(raw), new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("CountrySpecialist LLM fallback failed for {}: {}", destination, e.getMessage());
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
        return d;
    }
}
