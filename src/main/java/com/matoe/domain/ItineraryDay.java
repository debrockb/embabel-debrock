package com.matoe.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A single day in the day-by-day itinerary breakdown.
 */
public record ItineraryDay(
    @JsonProperty("dayNumber") int dayNumber,
    @JsonProperty("date") String date,
    @JsonProperty("title") String title,              // e.g. "Arrival & Montmartre Exploration"
    @JsonProperty("summary") String summary,
    @JsonProperty("morningActivities") List<String> morningActivities,
    @JsonProperty("afternoonActivities") List<String> afternoonActivities,
    @JsonProperty("eveningActivities") List<String> eveningActivities,
    @JsonProperty("meals") List<String> meals,        // restaurant/food suggestions
    @JsonProperty("transportNotes") String transportNotes,
    @JsonProperty("estimatedDayCost") double estimatedDayCost
) {}
