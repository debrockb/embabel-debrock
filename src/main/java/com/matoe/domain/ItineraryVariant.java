package com.matoe.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * One of three itinerary variants (Budget, Standard, Luxury) synthesised by the orchestrator.
 */
public record ItineraryVariant(
    @JsonProperty("tier") String tier,                    // "budget", "standard", "luxury"
    @JsonProperty("totalEstimatedCost") double totalEstimatedCost,
    @JsonProperty("accommodations") List<AccommodationOption> accommodations,
    @JsonProperty("transport") List<TransportOption> transport,
    @JsonProperty("attractions") List<AttractionOption> attractions,
    @JsonProperty("dayByDay") List<ItineraryDay> dayByDay,
    @JsonProperty("highlights") List<String> highlights,  // e.g. "Best value for couples"
    @JsonProperty("tradeoffs") String tradeoffs           // e.g. "Fewer amenities, longer transit"
) {}
