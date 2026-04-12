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
 * Hotel search agent.
 * Primary: browser-use visits Booking.com, Hotels.com, Expedia in parallel.
 * Fallback: LLM generates realistic hotel options when browser service is unavailable.
 */
@Component
public class HotelAgent {

    private static final Logger log = LoggerFactory.getLogger(HotelAgent.class);

    private final BrowserAgentService browserService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;

    @Value("${travel-agency.prompts.hotel-agent}")
    private String systemPrompt;

    @Value("${travel-agency.browser.hotel-sites:https://www.booking.com,https://www.hotels.com,https://www.expedia.com}")
    private String hotelSites;

    public HotelAgent(BrowserAgentService browserService, LlmService llmService,
                      ObjectMapper objectMapper, PromptTemplateService promptTemplateService) {
        this.browserService = browserService;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        this.promptTemplateService = promptTemplateService;
    }

    @Action(
        name = "search_hotels",
        preconditions = {"accommodation_search_requested"},
        effects = {"hotels_found"}
    )
    public List<AccommodationOption> searchHotels(TravelRequest request) {
        long nights = ChronoUnit.DAYS.between(request.startDate(), request.endDate());
        if (nights <= 0) nights = 1;

        // ── primary: real browser search ─────────────────────────────────────
        if (browserService.isAvailable()) {
            try {
                List<Map<String, Object>> raw = browserService.browseForList(
                    buildBrowserTask(request, nights),
                    Arrays.asList(hotelSites.split(",")),
                    "a JSON array of hotel objects each with: name, pricePerNight (number), " +
                    "totalPrice (number), rating (number 1-5), location (string), " +
                    "amenities (array of strings), bookingUrl (string)",
                    request.extractorModel()
                );
                if (raw != null && !raw.isEmpty()) {
                    log.info("HotelAgent: {} results via browser for {}", raw.size(), request.destination());
                    return raw.stream().map(m -> map(m, nights)).collect(Collectors.toList());
                }
            } catch (Exception e) {
                log.warn("HotelAgent browser search failed, falling back to LLM: {}", e.getMessage());
            }
        }

        // ── fallback: LLM-generated realistic options ─────────────────────────
        try {
            String userPrompt = promptTemplateService.buildHotelPrompt(systemPrompt, request);
            String raw = llmService.call(request.extractorModel(), "You are a travel search expert. Return ONLY valid JSON array.", userPrompt);
            List<Map<String, Object>> items = objectMapper.readValue(
                llmService.extractJson(raw), new TypeReference<>() {});
            return items.stream().map(m -> map(m, nights)).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("HotelAgent LLM fallback failed for {}: {}", request.destination(), e.getMessage());
            return List.of();
        }
    }

    private String buildBrowserTask(TravelRequest request, long nights) {
        return String.format(
            "Search for 4-6 hotels in %s for %d guests, check-in %s, check-out %s (%d nights). " +
            "Budget: €%.0f–€%.0f total. Travel style: %s. " +
            "For each hotel get: name, price per night, total price, star rating, neighborhood, amenities, booking URL.",
            request.destination(), request.guestCount(),
            request.startDate(), request.endDate(), nights,
            request.budgetMin(), request.budgetMax(), request.travelStyle()
        );
    }

    private AccommodationOption map(Map<String, Object> m, long nights) {
        double ppn  = num(m, "pricePerNight");
        double tot  = num(m, "totalPrice", ppn * nights);
        double avgPpn = tot / nights;
        String tier = avgPpn < 100 ? "budget" : avgPpn > 250 ? "luxury" : "standard";

        List<String> amenities = toStringList(m.get("amenities"));

        return new AccommodationOption(
            UUID.randomUUID().toString(), "hotel",
            str(m, "name"), ppn, tot,
            num(m, "rating"), str(m, "location"),
            amenities, str(m, "bookingUrl"), tier
        );
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private double num(Map<String, Object> m, String key) { return num(m, key, 0.0); }
    private double num(Map<String, Object> m, String key, double def) {
        Object v = m.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : def;
    }
    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key); return v != null ? v.toString() : "";
    }
    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object v) {
        if (v instanceof List<?> l) return l.stream().map(Object::toString).collect(Collectors.toList());
        return List.of();
    }
}
