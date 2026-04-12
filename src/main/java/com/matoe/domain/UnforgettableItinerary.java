package com.matoe.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record UnforgettableItinerary(
    @JsonProperty("id") String id,
    @JsonProperty("destination") String destination,
    @JsonProperty("startDate") String startDate,
    @JsonProperty("endDate") String endDate,
    @JsonProperty("guestCount") int guestCount,
    @JsonProperty("regionInsights") Map<String, Object> regionInsights,
    @JsonProperty("accommodations") List<AccommodationOption> accommodations,
    @JsonProperty("transport") List<TransportOption> transport,
    @JsonProperty("totalEstimatedCost") double totalEstimatedCost,
    @JsonProperty("createdAt") LocalDateTime createdAt
) {}
