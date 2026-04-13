package com.matoe.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * User travel request — the primary input to the agent swarm.
 * Supports multi-destination itineraries, interest tags, and per-request LLM model selection.
 */
public record TravelRequest(
    @JsonProperty("destination") String destination,
    @JsonProperty("destinations") List<String> destinations,       // multi-destination support
    @JsonProperty("startDate") LocalDate startDate,
    @JsonProperty("endDate") LocalDate endDate,
    @JsonProperty("guestCount") int guestCount,
    @JsonProperty("budgetMin") double budgetMin,
    @JsonProperty("budgetMax") double budgetMax,
    @JsonProperty("travelStyle") String travelStyle,               // "budget", "standard", "luxury"
    @JsonProperty("accommodationTypes") List<String> accommodationTypes, // "hotel", "bb", "apartment", "hostel"
    @JsonProperty("transportTypes") List<String> transportTypes,   // "flight", "car", "bus", "train", "ferry"
    @JsonProperty("interestTags") List<String> interestTags,       // "food", "history", "nature", "nightlife", etc.
    @JsonProperty("orchestratorModel") String orchestratorModel,
    @JsonProperty("extractorModel") String extractorModel,
    @JsonProperty("originCity") String originCity,                 // for transport routing
    @JsonProperty("sessionId") String sessionId                    // for SSE progress tracking
) {
    /** Backwards-compatible constructor (for requests missing new fields). */
    public TravelRequest {
        if (destinations == null || destinations.isEmpty()) {
            destinations = destination != null ? List.of(destination) : List.of();
        }
        if (accommodationTypes == null) accommodationTypes = List.of("hotel");
        if (transportTypes == null) transportTypes = List.of("flight");
        if (interestTags == null) interestTags = List.of();
        if (travelStyle == null) travelStyle = "standard";
        if (originCity == null) originCity = "";
        // Default to local LLMs so NAS installs work without cloud keys.
        if (orchestratorModel == null || orchestratorModel.isBlank())
            orchestratorModel = "lmstudio/qwen3.5:9b";
        if (extractorModel == null || extractorModel.isBlank())
            extractorModel = "lmstudio/nemotron-3-nano:4b";
    }

    public long nights() {
        long n = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        return n > 0 ? n : 1;
    }
}
