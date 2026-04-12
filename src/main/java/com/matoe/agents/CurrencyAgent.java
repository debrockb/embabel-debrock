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
 * Currency Agent — provides currency and money-handling intelligence for the destination.
 * LLM-only agent (no browser scraping needed).
 * Returns structured data on local currency, exchange rates, tipping customs, and payment methods.
 */
@Component
public class CurrencyAgent {

    private static final Logger log = LoggerFactory.getLogger(CurrencyAgent.class);

    private final BrowserAgentService browserService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;
    private final DynamicPromptService dynamicPromptService;
    private final LlmCostTrackingService costTracker;

    @Value("${travel-agency.prompts.currency-agent}")
    private String defaultPrompt;

    public CurrencyAgent(BrowserAgentService browserService, LlmService llmService,
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
        dynamicPromptService.registerDefault("currency-agent", defaultPrompt);
    }

    public Map<String, Object> getCurrencyInfo(TravelRequest request) {
        String model = request.extractorModel();

        try {
            String systemPrompt = dynamicPromptService.getPrompt("currency-agent");
            if (systemPrompt.isBlank()) systemPrompt = defaultPrompt;

            String userPrompt = String.format(
                "Provide currency and money-handling information for travelers visiting %s. " +
                "Return a JSON object with keys: " +
                "localCurrency (string, full name of currency), " +
                "currencyCode (string, ISO 4217 code like 'EUR' or 'JPY'), " +
                "exchangeRate (string, approximate rate vs USD like '1 USD = 0.92 EUR'), " +
                "tippingCustoms (string, local tipping etiquette and expected percentages), " +
                "atmAvailability (string, how easy it is to find ATMs and any fees to expect), " +
                "cardAcceptance (string, how widely credit/debit cards are accepted), " +
                "costOfLiving (string, general price level compared to major Western cities). " +
                "Return ONLY valid JSON.",
                request.destination()
            );

            long start = System.currentTimeMillis();
            String raw = llmService.call(model, systemPrompt, userPrompt);
            long durationMs = System.currentTimeMillis() - start;

            costTracker.logCall(request.sessionId(), "currency-agent", model != null ? model : "default",
                resolveProvider(model), estimateTokens(userPrompt), estimateTokens(raw), durationMs, true, null);

            return objectMapper.readValue(llmService.extractJson(raw), new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("CurrencyAgent LLM call failed for {}: {}", request.destination(), e.getMessage());
            costTracker.logCall(request.sessionId(), "currency-agent", request.extractorModel(),
                "unknown", 0, 0, 0, false, e.getMessage());
            return defaultCurrencyInfo(request.destination());
        }
    }

    private Map<String, Object> defaultCurrencyInfo(String destination) {
        Map<String, Object> d = new HashMap<>();
        d.put("localCurrency", "Check the local currency for " + destination);
        d.put("currencyCode", "N/A");
        d.put("exchangeRate", "Check current exchange rates before travel");
        d.put("tippingCustoms", "Research local tipping customs; 10-15% is common in many countries");
        d.put("atmAvailability", "ATMs are generally available in urban areas; carry some cash for rural regions");
        d.put("cardAcceptance", "Major credit cards are accepted in most tourist areas; carry cash as backup");
        d.put("costOfLiving", "Research cost of living to plan daily spending budget");
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
