package com.matoe.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AccommodationOption(
    @JsonProperty("id") String id,
    @JsonProperty("type") String type, // "hotel", "bb", "apartment"
    @JsonProperty("name") String name,
    @JsonProperty("pricePerNight") double pricePerNight,
    @JsonProperty("totalPrice") double totalPrice,
    @JsonProperty("rating") double rating,
    @JsonProperty("location") String location,
    @JsonProperty("amenities") java.util.List<String> amenities,
    @JsonProperty("bookingUrl") String bookingUrl,
    @JsonProperty("tier") String tier // "budget", "standard", "luxury"
) {}
