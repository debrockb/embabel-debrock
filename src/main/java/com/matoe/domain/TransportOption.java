package com.matoe.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TransportOption(
    @JsonProperty("id") String id,
    @JsonProperty("type") String type, // "flight", "car", "bus"
    @JsonProperty("provider") String provider,
    @JsonProperty("departureTime") String departureTime,
    @JsonProperty("arrivalTime") String arrivalTime,
    @JsonProperty("duration") String duration,
    @JsonProperty("price") double price,
    @JsonProperty("stops") int stops,
    @JsonProperty("bookingUrl") String bookingUrl,
    @JsonProperty("tier") String tier // "budget", "standard", "luxury"
) {}
