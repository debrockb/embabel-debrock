package com.matoe.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AccommodationOption(
    @JsonProperty("id") String id,
    @JsonProperty("type") String type,               // "hotel", "bb", "apartment", "hostel"
    @JsonProperty("name") String name,
    @JsonProperty("pricePerNight") double pricePerNight,
    @JsonProperty("totalPrice") double totalPrice,
    @JsonProperty("rating") double rating,
    @JsonProperty("location") String location,
    @JsonProperty("amenities") List<String> amenities,
    @JsonProperty("bookingUrl") String bookingUrl,
    @JsonProperty("tier") String tier,               // "budget", "standard", "luxury"
    @JsonProperty("source") String source,           // "browser", "llm", "api"  — provenance tracking
    @JsonProperty("imageUrl") String imageUrl,
    @JsonProperty("latitude") double latitude,
    @JsonProperty("longitude") double longitude
) {
    /** Backwards-compatible constructor (no coordinates). */
    public AccommodationOption(String id, String type, String name, double pricePerNight,
                               double totalPrice, double rating, String location,
                               List<String> amenities, String bookingUrl, String tier,
                               String source, String imageUrl) {
        this(id, type, name, pricePerNight, totalPrice, rating, location,
             amenities, bookingUrl, tier, source, imageUrl, 0.0, 0.0);
    }

    /** Backwards-compatible constructor (no source, imageUrl, or coordinates). */
    public AccommodationOption(String id, String type, String name, double pricePerNight,
                               double totalPrice, double rating, String location,
                               List<String> amenities, String bookingUrl, String tier) {
        this(id, type, name, pricePerNight, totalPrice, rating, location,
             amenities, bookingUrl, tier, "llm", "", 0.0, 0.0);
    }
}
