package com.matoe.agents;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matoe.domain.*;
import com.matoe.service.DynamicPromptService;
import com.matoe.service.LlmCostTrackingService;
import com.matoe.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Orchestrator Agent — the "brain" of M.A.T.O.E.
 * Takes all gathered data (accommodations, transport, attractions, insights)
 * and uses a cloud LLM to synthesize 3 itinerary variants (Budget, Standard, Luxury)
 * with day-by-day breakdowns.
 */
@Component
public class OrchestratorAgent {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorAgent.class);

    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final DynamicPromptService dynamicPromptService;
    private final LlmCostTrackingService costTracker;

    @Value("${travel-agency.prompts.orchestrator}")
    private String defaultPrompt;

    public OrchestratorAgent(LlmService llmService, ObjectMapper objectMapper,
                             DynamicPromptService dynamicPromptService,
                             LlmCostTrackingService costTracker) {
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        this.dynamicPromptService = dynamicPromptService;
        this.costTracker = costTracker;
    }

    @jakarta.annotation.PostConstruct
    void init() {
        dynamicPromptService.registerDefault("orchestrator", defaultPrompt);
    }

    /**
     * Synthesize the final itinerary from all gathered agent data.
     * Uses a cloud LLM to create 3 tiered variants with day-by-day breakdowns.
     */
    public UnforgettableItinerary synthesizeItinerary(
            TravelRequest request,
            List<AccommodationOption> accommodations,
            List<TransportOption> transport,
            List<AttractionOption> attractions,
            Map<String, Object> regionInsights,
            Map<String, Object> weatherForecast,
            Map<String, Object> currencyInfo) {

        String model = request.orchestratorModel();
        if (model == null || model.isBlank()) model = "lmstudio/llama-3-8b";

        // Build the synthesis prompt with all gathered data
        String systemPrompt = dynamicPromptService.getPrompt("orchestrator");
        if (systemPrompt.isBlank()) systemPrompt = defaultPrompt;
        String userPrompt = buildSynthesisPrompt(request, accommodations, transport,
            attractions, regionInsights, weatherForecast, currencyInfo);

        List<ItineraryVariant> variants = List.of();

        try {
            long start = System.currentTimeMillis();
            String raw = llmService.call(model, systemPrompt, userPrompt);
            long durationMs = System.currentTimeMillis() - start;

            costTracker.logCall(request.sessionId(), "orchestrator", model,
                resolveProvider(model), estimateTokens(userPrompt + systemPrompt),
                estimateTokens(raw), durationMs, true, null);

            String json = llmService.extractJson(raw);
            Map<String, Object> synthesized = objectMapper.readValue(json, new TypeReference<>() {});

            variants = parseVariants(synthesized, request);
            log.info("Orchestrator synthesized {} variants for {}", variants.size(), request.destination());

        } catch (Exception e) {
            log.warn("Orchestrator LLM synthesis failed, building fallback: {}", e.getMessage());
            costTracker.logCall(request.sessionId(), "orchestrator", model,
                resolveProvider(model), 0, 0, 0, false, e.getMessage());
            variants = buildFallbackVariants(accommodations, transport, attractions, request);
        }

        double totalCost = variants.stream()
            .filter(v -> "standard".equals(v.tier()))
            .mapToDouble(ItineraryVariant::totalEstimatedCost)
            .findFirst().orElse(
                accommodations.stream().mapToDouble(AccommodationOption::totalPrice).sum() +
                transport.stream().mapToDouble(TransportOption::price).sum()
            );

        return new UnforgettableItinerary(
            UUID.randomUUID().toString(),
            request.destination(),
            request.startDate().toString(),
            request.endDate().toString(),
            request.guestCount(),
            regionInsights != null ? regionInsights : Map.of(),
            accommodations != null ? accommodations : List.of(),
            transport != null ? transport : List.of(),
            attractions != null ? attractions : List.of(),
            variants,
            totalCost,
            weatherForecast != null ? weatherForecast : Map.of(),
            currencyInfo != null ? currencyInfo : Map.of(),
            LocalDateTime.now()
        );
    }

    private String buildSynthesisPrompt(TravelRequest request,
            List<AccommodationOption> accommodations, List<TransportOption> transport,
            List<AttractionOption> attractions, Map<String, Object> insights,
            Map<String, Object> weather, Map<String, Object> currency) {

        StringBuilder sb = new StringBuilder();
        sb.append("Plan a ").append(request.nights()).append("-day trip to ").append(request.destination());
        sb.append(" for ").append(request.guestCount()).append(" guests, ");
        sb.append(request.startDate()).append(" to ").append(request.endDate()).append(".\n");
        sb.append("Budget: €").append(String.format("%.0f", request.budgetMin()));
        sb.append("–€").append(String.format("%.0f", request.budgetMax())).append(" total.\n");
        sb.append("Travel style preference: ").append(request.travelStyle()).append(".\n\n");

        if (insights != null && !insights.isEmpty()) {
            sb.append("=== REGIONAL INSIGHTS ===\n");
            insights.forEach((k, v) -> sb.append(k).append(": ").append(v).append("\n"));
            sb.append("\n");
        }
        if (weather != null && !weather.isEmpty()) {
            sb.append("=== WEATHER ===\n");
            weather.forEach((k, v) -> sb.append(k).append(": ").append(v).append("\n"));
            sb.append("\n");
        }
        if (currency != null && !currency.isEmpty()) {
            sb.append("=== CURRENCY ===\n");
            currency.forEach((k, v) -> sb.append(k).append(": ").append(v).append("\n"));
            sb.append("\n");
        }

        sb.append("=== AVAILABLE ACCOMMODATIONS (").append(accommodations.size()).append(") ===\n");
        for (AccommodationOption a : accommodations) {
            sb.append("- ").append(a.name()).append(" (").append(a.type()).append(", ")
              .append(a.tier()).append("): €").append(String.format("%.0f", a.pricePerNight()))
              .append("/night, total €").append(String.format("%.0f", a.totalPrice()))
              .append(", rating ").append(a.rating()).append(", ").append(a.location()).append("\n");
        }

        sb.append("\n=== AVAILABLE TRANSPORT (").append(transport.size()).append(") ===\n");
        for (TransportOption t : transport) {
            sb.append("- ").append(t.provider()).append(" (").append(t.type()).append(", ")
              .append(t.tier()).append("): €").append(String.format("%.0f", t.price()))
              .append(", ").append(t.duration()).append(", ").append(t.stops()).append(" stops\n");
        }

        if (attractions != null && !attractions.isEmpty()) {
            sb.append("\n=== AVAILABLE ATTRACTIONS (").append(attractions.size()).append(") ===\n");
            for (AttractionOption attr : attractions) {
                sb.append("- ").append(attr.name()).append(" (").append(attr.category())
                  .append("): €").append(String.format("%.0f", attr.price()))
                  .append(", ").append(attr.duration()).append("\n");
            }
        }

        sb.append("\n=== INSTRUCTIONS ===\n");
        sb.append("Create 3 itinerary variants:\n");
        sb.append("1. BUDGET — cheapest viable option\n");
        sb.append("2. STANDARD — best balance of value and comfort\n");
        sb.append("3. LUXURY — premium experience\n\n");
        sb.append("For each variant, provide:\n");
        sb.append("- tier (\"budget\", \"standard\", \"luxury\")\n");
        sb.append("- totalEstimatedCost (number)\n");
        sb.append("- selectedAccommodations (array of accommodation names)\n");
        sb.append("- selectedTransport (array of transport provider names)\n");
        sb.append("- selectedAttractions (array of attraction names)\n");
        sb.append("- highlights (array of 2-3 selling points)\n");
        sb.append("- tradeoffs (string describing what you gain/lose)\n");
        sb.append("- dayByDay (array of day objects with: dayNumber, date, title, summary, ");
        sb.append("morningActivities[], afternoonActivities[], eveningActivities[], meals[], ");
        sb.append("transportNotes, estimatedDayCost)\n\n");
        sb.append("Return a JSON object with key \"variants\" containing an array of 3 variant objects.\n");
        sb.append("Return ONLY valid JSON, no explanation.");

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private List<ItineraryVariant> parseVariants(Map<String, Object> synthesized, TravelRequest request) {
        Object variantsRaw = synthesized.get("variants");
        if (!(variantsRaw instanceof List<?> list)) return List.of();

        List<ItineraryVariant> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> v)) continue;
            Map<String, Object> vm = (Map<String, Object>) v;

            List<ItineraryDay> days = parseDays(vm.get("dayByDay"), request);
            result.add(new ItineraryVariant(
                str(vm, "tier"),
                num(vm, "totalEstimatedCost"),
                List.of(), // accommodation objects filled by TravelService later
                List.of(),
                List.of(),
                days,
                toStringList(vm.get("highlights")),
                str(vm, "tradeoffs")
            ));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<ItineraryDay> parseDays(Object dayByDayRaw, TravelRequest request) {
        if (!(dayByDayRaw instanceof List<?> dayList)) return generateDefaultDays(request);
        List<ItineraryDay> days = new ArrayList<>();
        for (Object d : dayList) {
            if (!(d instanceof Map<?, ?> dm)) continue;
            Map<String, Object> day = (Map<String, Object>) d;
            days.add(new ItineraryDay(
                ((Number) day.getOrDefault("dayNumber", days.size() + 1)).intValue(),
                str(day, "date"),
                str(day, "title"),
                str(day, "summary"),
                toStringList(day.get("morningActivities")),
                toStringList(day.get("afternoonActivities")),
                toStringList(day.get("eveningActivities")),
                toStringList(day.get("meals")),
                str(day, "transportNotes"),
                num(day, "estimatedDayCost")
            ));
        }
        return days;
    }

    List<ItineraryDay> generateDefaultDays(TravelRequest request) {
        List<ItineraryDay> days = new ArrayList<>();
        long nights = request.nights();
        for (int i = 1; i <= nights; i++) {
            LocalDate date = request.startDate().plusDays(i - 1);
            String title = i == 1 ? "Arrival Day" : i == nights ? "Departure Day" : "Day " + i;
            days.add(new ItineraryDay(i, date.toString(), title,
                "Explore " + request.destination(),
                List.of("Morning activity"), List.of("Afternoon activity"),
                List.of("Evening activity"), List.of("Local restaurant"),
                "", 0));
        }
        return days;
    }

    List<ItineraryVariant> buildFallbackVariants(
            List<AccommodationOption> accommodations, List<TransportOption> transport,
            List<AttractionOption> attractions, TravelRequest request) {
        // Group by tier and build basic variants
        List<ItineraryVariant> variants = new ArrayList<>();
        for (String tier : List.of("budget", "standard", "luxury")) {
            List<AccommodationOption> tierAccom = accommodations.stream()
                .filter(a -> tier.equals(a.tier())).toList();
            List<TransportOption> tierTransport = transport.stream()
                .filter(t -> tier.equals(t.tier())).toList();
            List<AttractionOption> tierAttract = attractions != null ? attractions.stream()
                .filter(a -> tier.equals(a.tier())).toList() : List.of();

            double cost = tierAccom.stream().mapToDouble(AccommodationOption::totalPrice).sum()
                        + tierTransport.stream().mapToDouble(TransportOption::price).sum()
                        + tierAttract.stream().mapToDouble(AttractionOption::price).sum();

            variants.add(new ItineraryVariant(tier, cost, tierAccom, tierTransport, tierAttract,
                generateDefaultDays(request),
                List.of(tier.substring(0, 1).toUpperCase() + tier.substring(1) + " option"),
                "Fallback variant — LLM synthesis unavailable"));
        }
        return variants;
    }

    private double num(Map<String, Object> m, String k) {
        Object v = m.get(k); return v instanceof Number n ? n.doubleValue() : 0.0; }
    private String str(Map<String, Object> m, String k) {
        Object v = m.get(k); return v != null ? v.toString() : ""; }
    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object v) {
        if (v instanceof List<?> l) return l.stream().map(Object::toString).collect(java.util.stream.Collectors.toList());
        return List.of(); }
    private String resolveProvider(String model) {
        if (model == null) return "anthropic";
        if (model.startsWith("anthropic/")) return "anthropic";
        if (model.startsWith("lmstudio/")) return "lmstudio";
        if (model.startsWith("ollama/")) return "ollama";
        if (model.startsWith("openrouter/")) return "openrouter";
        return "unknown"; }
    private int estimateTokens(String text) { return text != null ? text.length() / 4 : 0; }
}
