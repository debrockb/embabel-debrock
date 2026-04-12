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
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Ground transport agent (car rental + bus).
 * Primary: browser-use visits Rentalcars.com, FlixBus, BlaBlaCar.
 * Fallback: LLM-generated results.
 */
@Component
public class CarBusAgent {

    private static final Logger log = LoggerFactory.getLogger(CarBusAgent.class);

    private final BrowserAgentService browserService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;

    @Value("${travel-agency.prompts.car-bus-agent}")
    private String systemPrompt;

    @Value("${travel-agency.browser.ground-transport-sites:https://www.rentalcars.com,https://www.flixbus.com,https://www.blablacar.com}")
    private String groundTransportSites;

    public CarBusAgent(BrowserAgentService browserService, LlmService llmService,
                       ObjectMapper objectMapper, PromptTemplateService promptTemplateService) {
        this.browserService = browserService;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        this.promptTemplateService = promptTemplateService;
    }

    @Action(name = "search_ground_transport", preconditions = {"transport_search_requested"}, effects = {"ground_transport_found"})
    public List<TransportOption> searchGroundTransport(TravelRequest request) {
        long days = ChronoUnit.DAYS.between(request.startDate(), request.endDate());
        if (days <= 0) days = 1;

        if (browserService.isAvailable()) {
            try {
                List<Map<String, Object>> raw = browserService.browseForList(
                    String.format("Find 3-4 ground transport options (car rental and/or bus) " +
                        "to/from %s for %d people, %s to %s (%d days). Budget €%.0f max total.",
                        request.destination(), request.guestCount(),
                        request.startDate(), request.endDate(), days, request.budgetMax()),
                    Arrays.asList(groundTransportSites.split(",")),
                    "JSON array: type ('car' or 'bus'), provider, departureTime (HH:mm), " +
                    "arrivalTime (HH:mm), duration, price (total trip, number), bookingUrl",
                    request.extractorModel()
                );
                if (raw != null && !raw.isEmpty()) {
                    return raw.stream().map(this::map).collect(Collectors.toList());
                }
            } catch (Exception e) {
                log.warn("CarBusAgent browser failed: {}", e.getMessage());
            }
        }

        try {
            String prompt = promptTemplateService.buildCarBusPrompt(systemPrompt, request);
            String raw = llmService.call(request.extractorModel(), "Return ONLY valid JSON array.", prompt);
            List<Map<String, Object>> items = objectMapper.readValue(llmService.extractJson(raw), new TypeReference<>() {});
            return items.stream().map(this::map).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("CarBusAgent LLM fallback failed: {}", e.getMessage());
            return List.of();
        }
    }

    private TransportOption map(Map<String, Object> m) {
        double price = num(m, "price");
        String tier = price < 100 ? "budget" : price > 400 ? "luxury" : "standard";
        String type = str(m, "type");
        if (!type.equals("car") && !type.equals("bus")) type = "car";
        return new TransportOption(UUID.randomUUID().toString(), type,
            str(m, "provider"), str(m, "departureTime"), str(m, "arrivalTime"),
            str(m, "duration"), price, 0, str(m, "bookingUrl"), tier);
    }

    private double num(Map<String, Object> m, String k) {
        Object v = m.get(k); return v instanceof Number n ? n.doubleValue() : 0.0; }
    private String str(Map<String, Object> m, String k) { Object v = m.get(k); return v != null ? v.toString() : ""; }
}
