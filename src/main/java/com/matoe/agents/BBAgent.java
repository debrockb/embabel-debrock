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
 * B&B / guesthouse search agent.
 * Primary: browser-use visits Airbnb, BedandBreakfast.eu, local tourism sites.
 * Fallback: LLM generates realistic B&B options.
 */
@Component
public class BBAgent {

    private static final Logger log = LoggerFactory.getLogger(BBAgent.class);

    private final BrowserAgentService browserService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;

    @Value("${travel-agency.prompts.bb-agent}")
    private String systemPrompt;

    @Value("${travel-agency.browser.bb-sites:https://www.airbnb.com,https://www.bedandbreakfast.eu}")
    private String bbSites;

    public BBAgent(BrowserAgentService browserService, LlmService llmService,
                   ObjectMapper objectMapper, PromptTemplateService promptTemplateService) {
        this.browserService = browserService;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        this.promptTemplateService = promptTemplateService;
    }

    @Action(name = "search_bb", preconditions = {"accommodation_search_requested"}, effects = {"bb_found"})
    public List<AccommodationOption> searchBB(TravelRequest request) {
        long nights = ChronoUnit.DAYS.between(request.startDate(), request.endDate());
        if (nights <= 0) nights = 1;

        if (browserService.isAvailable()) {
            try {
                List<Map<String, Object>> raw = browserService.browseForList(
                    String.format("Find 3-5 bed-and-breakfasts or charming guesthouses in %s " +
                        "for %d guests, %s to %s. Budget €%.0f–%.0f total.",
                        request.destination(), request.guestCount(),
                        request.startDate(), request.endDate(),
                        request.budgetMin(), request.budgetMax()),
                    Arrays.asList(bbSites.split(",")),
                    "JSON array: name, pricePerNight, totalPrice, rating, location, amenities[], bookingUrl",
                    request.extractorModel()
                );
                if (raw != null && !raw.isEmpty()) {
                    log.info("BBAgent: {} results via browser", raw.size());
                    return raw.stream().map(m -> map(m, nights)).collect(Collectors.toList());
                }
            } catch (Exception e) {
                log.warn("BBAgent browser failed, falling back: {}", e.getMessage());
            }
        }

        try {
            String prompt = promptTemplateService.buildBBPrompt(systemPrompt, request);
            String raw = llmService.call(request.extractorModel(), "Return ONLY valid JSON array.", prompt);
            List<Map<String, Object>> items = objectMapper.readValue(llmService.extractJson(raw), new TypeReference<>() {});
            return items.stream().map(m -> map(m, nights)).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("BBAgent LLM fallback failed: {}", e.getMessage());
            return List.of();
        }
    }

    private AccommodationOption map(Map<String, Object> m, long nights) {
        double ppn = num(m, "pricePerNight");
        double tot = num(m, "totalPrice", ppn * nights);
        double avgPpn = tot / nights;
        String tier = avgPpn < 100 ? "budget" : avgPpn > 200 ? "luxury" : "standard";
        return new AccommodationOption(UUID.randomUUID().toString(), "bb",
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
