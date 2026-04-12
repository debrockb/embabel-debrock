package com.matoe.agents;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matoe.annotations.Action;
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
 * Weather Agent — provides weather forecast and packing advice for the destination.
 * LLM-only agent (no browser scraping needed).
 * Returns structured weather data including temperature ranges, precipitation, and packing tips.
 */
@Component
public class WeatherAgent {

    private static final Logger log = LoggerFactory.getLogger(WeatherAgent.class);

    private final BrowserAgentService browserService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;
    private final DynamicPromptService dynamicPromptService;
    private final LlmCostTrackingService costTracker;

    @Value("${travel-agency.prompts.weather-agent}")
    private String defaultPrompt;

    public WeatherAgent(BrowserAgentService browserService, LlmService llmService,
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
        dynamicPromptService.registerDefault("weather-agent", defaultPrompt);
    }

    @Action(name = "get_weather_forecast", preconditions = {"destination_provided"}, effects = {"weather_forecast_available"})
    public Map<String, Object> getWeatherForecast(TravelRequest request) {
        String model = request.extractorModel();

        try {
            String systemPrompt = dynamicPromptService.getPrompt("weather-agent");
            if (systemPrompt.isBlank()) systemPrompt = defaultPrompt;

            String userPrompt = String.format(
                "Provide a weather forecast for %s during the travel period %s to %s. " +
                "Return a JSON object with keys: averageTemp (number in Celsius), " +
                "highTemp (number in Celsius), lowTemp (number in Celsius), " +
                "precipitation (string description like 'moderate rainfall expected'), " +
                "humidity (string like 'high' or '70%%'), " +
                "packingTips (array of strings with specific clothing/gear recommendations), " +
                "weatherWarnings (array of strings with any seasonal alerts or concerns). " +
                "Return ONLY valid JSON.",
                request.destination(), request.startDate(), request.endDate()
            );

            long start = System.currentTimeMillis();
            String raw = llmService.call(model, systemPrompt, userPrompt);
            long durationMs = System.currentTimeMillis() - start;

            costTracker.logCall(request.sessionId(), "weather-agent", model != null ? model : "default",
                resolveProvider(model), estimateTokens(userPrompt), estimateTokens(raw), durationMs, true, null);

            return objectMapper.readValue(llmService.extractJson(raw), new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("WeatherAgent LLM call failed for {}: {}", request.destination(), e.getMessage());
            costTracker.logCall(request.sessionId(), "weather-agent", request.extractorModel(),
                "unknown", 0, 0, 0, false, e.getMessage());
            return defaultForecast(request.destination());
        }
    }

    private Map<String, Object> defaultForecast(String destination) {
        Map<String, Object> d = new HashMap<>();
        d.put("averageTemp", 20);
        d.put("highTemp", 25);
        d.put("lowTemp", 15);
        d.put("precipitation", "Check local forecast closer to travel date");
        d.put("humidity", "Moderate");
        d.put("packingTips", List.of(
            "Pack layers for variable temperatures",
            "Bring a light rain jacket",
            "Comfortable walking shoes recommended"
        ));
        d.put("weatherWarnings", List.of(
            "Weather data is estimated; check an up-to-date forecast before departure"
        ));
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
