package com.matoe.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AttractionOption(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("category") String category,       // "museum", "tour", "food", "nature", "nightlife", etc.
    @JsonProperty("price") double price,
    @JsonProperty("duration") String duration,        // e.g. "2h", "half-day"
    @JsonProperty("rating") double rating,
    @JsonProperty("location") String location,
    @JsonProperty("bookingUrl") String bookingUrl,
    @JsonProperty("tier") String tier,
    @JsonProperty("source") String source,
    @JsonProperty("tags") List<String> tags,
    @JsonProperty("latitude") double latitude,
    @JsonProperty("longitude") double longitude
) {
    /** Backwards-compatible constructor (no coordinates). */
    public AttractionOption(String id, String name, String description, String category,
                            double price, String duration, double rating, String location,
                            String bookingUrl, String tier, String source, List<String> tags) {
        this(id, name, description, category, price, duration, rating, location,
             bookingUrl, tier, source, tags, 0.0, 0.0);
    }
}
