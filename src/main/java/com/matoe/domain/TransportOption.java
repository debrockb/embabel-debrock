package com.matoe.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TransportOption(
    @JsonProperty("id") String id,
    @JsonProperty("type") String type,               // "flight", "car", "bus", "train", "ferry"
    @JsonProperty("provider") String provider,
    @JsonProperty("departureTime") String departureTime,
    @JsonProperty("arrivalTime") String arrivalTime,
    @JsonProperty("duration") String duration,
    @JsonProperty("price") double price,
    @JsonProperty("stops") int stops,
    @JsonProperty("bookingUrl") String bookingUrl,
    @JsonProperty("tier") String tier,               // "budget", "standard", "luxury"
    @JsonProperty("source") String source,           // "browser", "llm", "api"
    @JsonProperty("origin") String origin,
    @JsonProperty("destination") String destination
) {
    /** Backwards-compatible constructor. */
    public TransportOption(String id, String type, String provider, String departureTime,
                           String arrivalTime, String duration, double price, int stops,
                           String bookingUrl, String tier) {
        this(id, type, provider, departureTime, arrivalTime, duration, price, stops,
             bookingUrl, tier, "llm", "", "");
    }
}
