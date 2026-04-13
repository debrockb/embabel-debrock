package com.matoe.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * User travel request — the primary input to the agent swarm.
 * Supports multi-destination itineraries, interest tags, per-request LLM model selection,
 * and detailed traveller composition (adults, children with ages, rooms).
 */
public record TravelRequest(
    @JsonProperty("destination") String destination,
    @JsonProperty("destinations") List<String> destinations,
    @JsonProperty("startDate") LocalDate startDate,
    @JsonProperty("endDate") LocalDate endDate,
    @JsonProperty("guestCount") int guestCount,                     // total travellers (adults + children)
    @JsonProperty("adults") int adults,
    @JsonProperty("children") int children,
    @JsonProperty("childrenAges") List<Integer> childrenAges,       // per-child age for pricing
    @JsonProperty("rooms") int rooms,
    @JsonProperty("budgetMin") double budgetMin,
    @JsonProperty("budgetMax") double budgetMax,
    @JsonProperty("travelStyle") String travelStyle,                // "budget", "standard", "luxury"
    @JsonProperty("accommodationTypes") List<String> accommodationTypes,
    @JsonProperty("transportTypes") List<String> transportTypes,
    @JsonProperty("interestTags") List<String> interestTags,
    @JsonProperty("mealPlan") String mealPlan,                       // "room-only", "breakfast", "half-board", "full-board", "all-inclusive"
    @JsonProperty("orchestratorModel") String orchestratorModel,
    @JsonProperty("extractorModel") String extractorModel,
    @JsonProperty("originCity") String originCity,
    @JsonProperty("sessionId") String sessionId
) {
    /** Backwards-compatible compact constructor — fills in sane defaults for any missing field. */
    public TravelRequest {
        if (destinations == null || destinations.isEmpty()) {
            destinations = destination != null ? List.of(destination) : List.of();
        }
        if (accommodationTypes == null) accommodationTypes = List.of("hotel");
        if (transportTypes == null) transportTypes = List.of("flight");
        if (interestTags == null) interestTags = List.of();
        if (childrenAges == null) childrenAges = List.of();
        if (travelStyle == null) travelStyle = "standard";
        if (mealPlan == null || mealPlan.isBlank()) mealPlan = "breakfast";
        if (originCity == null) originCity = "";

        // Reconcile adults/children/guestCount for backwards compatibility.
        // Old clients send guestCount only; new clients send adults+children.
        if (adults <= 0 && children <= 0 && guestCount > 0) {
            adults = guestCount;   // legacy: treat all guests as adults
        }
        if (adults <= 0) adults = 1;
        guestCount = adults + children;
        if (rooms <= 0) rooms = 1;

        // Trim childrenAges to match declared children count
        if (childrenAges.size() > children) {
            childrenAges = childrenAges.subList(0, children);
        }

        // Default to local LLMs so NAS installs work without cloud keys.
        if (orchestratorModel == null || orchestratorModel.isBlank())
            orchestratorModel = "lmstudio/nemotron-3-nano:4b";
        if (extractorModel == null || extractorModel.isBlank())
            extractorModel = "lmstudio/nemotron-3-nano:4b";
    }

    public long nights() {
        long n = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        return n > 0 ? n : 1;
    }

    /** Comma-separated children ages for prompt templates, e.g. "3, 7, 12". */
    public String childrenAgesText() {
        if (childrenAges == null || childrenAges.isEmpty()) return "";
        return childrenAges.stream().map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse("");
    }
}
