package com.matoe.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * The final synthesised itinerary returned to the user.
 * Contains 3 variants (Budget/Standard/Luxury), regional insights, and all raw search results.
 */
public record UnforgettableItinerary(
    @JsonProperty("id") String id,
    @JsonProperty("destination") String destination,
    @JsonProperty("startDate") String startDate,
    @JsonProperty("endDate") String endDate,
    @JsonProperty("guestCount") int guestCount,
    @JsonProperty("regionInsights") Map<String, Object> regionInsights,
    @JsonProperty("accommodations") List<AccommodationOption> accommodations,
    @JsonProperty("transport") List<TransportOption> transport,
    @JsonProperty("attractions") List<AttractionOption> attractions,
    @JsonProperty("variants") List<ItineraryVariant> variants,
    @JsonProperty("totalEstimatedCost") double totalEstimatedCost,
    @JsonProperty("weatherForecast") Map<String, Object> weatherForecast,
    @JsonProperty("currencyInfo") Map<String, Object> currencyInfo,
    @JsonProperty("createdAt") LocalDateTime createdAt
) {
    /** Backwards-compatible constructor (no variants, attractions, weather, currency). */
    public UnforgettableItinerary(String id, String destination, String startDate, String endDate,
                                  int guestCount, Map<String, Object> regionInsights,
                                  List<AccommodationOption> accommodations, List<TransportOption> transport,
                                  double totalEstimatedCost, LocalDateTime createdAt) {
        this(id, destination, startDate, endDate, guestCount, regionInsights,
             accommodations, transport, List.of(), List.of(),
             totalEstimatedCost, Map.of(), Map.of(), createdAt);
    }
}
