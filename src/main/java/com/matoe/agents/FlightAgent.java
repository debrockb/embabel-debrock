package com.matoe.agents;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matoe.annotations.Action;
import com.matoe.domain.TransportOption;
import com.matoe.domain.TravelRequest;
import com.matoe.service.BrowserAgentService;
import com.matoe.service.LlmService;
import com.matoe.service.PromptTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Flight search agent.
 * Primary: browser-use visits Skyscanner, Google Flights, Kayak.
 * Fallback: LLM-generated realistic flight options.
 */
@Component
public class FlightAgent {

    private static final Logger log = LoggerFactory.getLogger(FlightAgent.class);

    private final BrowserAgentService browserService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;

    @Value("${travel-agency.prompts.flight-agent}")
    private String systemPrompt;

    @Value("${travel-agency.browser.flight-sites:https://www.skyscanner.com,https://www.google.com/flights,https://www.kayak.com}")
    private String flightSites;

    public FlightAgent(BrowserAgentService browserService, LlmService llmService,
                       ObjectMapper objectMapper, PromptTemplateService promptTemplateService) {
        this.browserService = browserService;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        this.promptTemplateService = promptTemplateService;
    }

    @Action(name = "search_flights", preconditions = {"transport_search_requested"}, effects = {"flights_found"})
    public List<TransportOption> searchFlights(TravelRequest request) {

        if (browserService.isAvailable()) {
            try {
                List<Map<String, Object>> raw = browserService.browseForList(
                    String.format("Find 4-6 flights for %d passengers to %s, " +
                        "departing %s returning %s. Max budget €%.0f per person. " +
                        "Include budget and premium options.",
                        request.guestCount(), request.destination(),
                        request.startDate(), request.endDate(), request.budgetMax()),
                    Arrays.asList(flightSites.split(",")),
                    "JSON array: airline, departureTime (HH:mm), arrivalTime (HH:mm), " +
                    "duration (e.g. '2h 30m'), stops (integer), price (per person, number), bookingUrl",
                    request.extractorModel()
                );
                if (raw != null && !raw.isEmpty()) {
                    log.info("FlightAgent: {} results via browser", raw.size());
                    return raw.stream().map(this::map).collect(Collectors.toList());
                }
            } catch (Exception e) {
                log.warn("FlightAgent browser failed: {}", e.getMessage());
            }
        }

        try {
            String prompt = promptTemplateService.buildFlightPrompt(systemPrompt, request);
            String raw = llmService.call(request.extractorModel(), "Return ONLY valid JSON array.", prompt);
            List<Map<String, Object>> items = objectMapper.readValue(llmService.extractJson(raw), new TypeReference<>() {});
            return items.stream().map(this::map).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("FlightAgent LLM fallback failed: {}", e.getMessage());
            return List.of();
        }
    }

    private TransportOption map(Map<String, Object> m) {
        double price = num(m, "price");
        String tier = price < 150 ? "budget" : price > 500 ? "luxury" : "standard";
        return new TransportOption(UUID.randomUUID().toString(), "flight",
            str(m, "airline"), str(m, "departureTime"), str(m, "arrivalTime"),
            str(m, "duration"), price, ((Number) m.getOrDefault("stops", 0)).intValue(),
            str(m, "bookingUrl"), tier);
    }

    private double num(Map<String, Object> m, String k) {
        Object v = m.get(k); return v instanceof Number n ? n.doubleValue() : 0.0; }
    private String str(Map<String, Object> m, String k) { Object v = m.get(k); return v != null ? v.toString() : ""; }
}
