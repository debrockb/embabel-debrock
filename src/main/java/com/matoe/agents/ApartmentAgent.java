package com.matoe.agents;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matoe.annotations.Action;
import com.matoe.domain.AccommodationOption;
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
 * Apartment / holiday-rental search agent.
 * Primary: browser-use visits Airbnb, Vrbo, HomeAway.
 * Fallback: LLM-generated results.
 */
@Component
public class ApartmentAgent {

    private static final Logger log = LoggerFactory.getLogger(ApartmentAgent.class);

    private final BrowserAgentService browserService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;

    @Value("${travel-agency.prompts.apartment-agent}")
    private String systemPrompt;

    @Value("${travel-agency.browser.apartment-sites:https://www.airbnb.com,https://www.vrbo.com}")
    private String apartmentSites;

    public ApartmentAgent(BrowserAgentService browserService, LlmService llmService,
                          ObjectMapper objectMapper, PromptTemplateService promptTemplateService) {
        this.browserService = browserService;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        this.promptTemplateService = promptTemplateService;
    }

    @Action(name = "search_apartments", preconditions = {"accommodation_search_requested"}, effects = {"apartments_found"})
    public List<AccommodationOption> searchApartments(TravelRequest request) {
        long nights = ChronoUnit.DAYS.between(request.startDate(), request.endDate());
        if (nights <= 0) nights = 1;

        if (browserService.isAvailable()) {
            try {
                List<Map<String, Object>> raw = browserService.browseForList(
                    String.format("Find 3-5 holiday apartments in %s for %d guests, " +
                        "%s to %s (%d nights). Need %d+ bedrooms. Budget €%.0f–%.0f.",
                        request.destination(), request.guestCount(),
                        request.startDate(), request.endDate(), nights,
                        Math.max(1, request.guestCount() / 2),
                        request.budgetMin(), request.budgetMax()),
                    Arrays.asList(apartmentSites.split(",")),
                    "JSON array: name, pricePerNight, totalPrice, rating, location, amenities[], bookingUrl",
                    request.extractorModel()
                );
                if (raw != null && !raw.isEmpty()) {
                    return raw.stream().map(m -> map(m, nights)).collect(Collectors.toList());
                }
            } catch (Exception e) {
                log.warn("ApartmentAgent browser failed: {}", e.getMessage());
            }
        }

        try {
            String prompt = promptTemplateService.buildApartmentPrompt(systemPrompt, request);
            String raw = llmService.call(request.extractorModel(), "Return ONLY valid JSON array.", prompt);
            List<Map<String, Object>> items = objectMapper.readValue(llmService.extractJson(raw), new TypeReference<>() {});
            return items.stream().map(m -> map(m, nights)).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("ApartmentAgent LLM fallback failed: {}", e.getMessage());
            return List.of();
        }
    }

    private AccommodationOption map(Map<String, Object> m, long nights) {
        double ppn = num(m, "pricePerNight");
        double tot = num(m, "totalPrice", ppn * nights);
        double avgPpn = tot / nights;
        String tier = avgPpn < 100 ? "budget" : avgPpn > 250 ? "luxury" : "standard";
        return new AccommodationOption(UUID.randomUUID().toString(), "apartment",
            str(m, "name"), ppn, tot, num(m, "rating"), str(m, "location"),
            toStringList(m.get("amenities")), str(m, "bookingUrl"), tier);
    }

    private double num(Map<String, Object> m, String k) { return num(m, k, 0.0); }
    private double num(Map<String, Object> m, String k, double d) {
        Object v = m.get(k); return v instanceof Number n ? n.doubleValue() : d; }
    private String str(Map<String, Object> m, String k) { Object v = m.get(k); return v != null ? v.toString() : ""; }
    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object v) {
        if (v instanceof List<?> l) return l.stream().map(Object::toString).collect(Collectors.toList());
        return List.of();
    }
}
