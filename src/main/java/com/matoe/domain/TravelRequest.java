package com.matoe.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

public record TravelRequest(
    @JsonProperty("destination") String destination,
    @JsonProperty("startDate") LocalDate startDate,
    @JsonProperty("endDate") LocalDate endDate,
    @JsonProperty("guestCount") int guestCount,
    @JsonProperty("budgetMin") double budgetMin,
    @JsonProperty("budgetMax") double budgetMax,
    @JsonProperty("travelStyle") String travelStyle, // "budget", "standard", "luxury"
    @JsonProperty("accommodationTypes") List<String> accommodationTypes, // "hotel", "bb", "apartment"
    @JsonProperty("transportTypes") List<String> transportTypes, // "flight", "car", "bus"
    @JsonProperty("orchestratorModel") String orchestratorModel,
    @JsonProperty("extractorModel") String extractorModel
) {}
