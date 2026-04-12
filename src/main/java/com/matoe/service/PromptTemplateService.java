package com.matoe.service;

import com.matoe.domain.TravelRequest;
import org.springframework.stereotype.Service;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for managing prompt templates with variable substitution.
 * Prompts in application.yml can contain {{variable}} placeholders that are replaced
 * with actual values at runtime.
 */
@Service
public class PromptTemplateService {

    /**
     * Build a user prompt for hotel search by substituting variables into the template.
     */
    public String buildHotelPrompt(String template, TravelRequest request) {
        long nights = calculateNights(request);
        Map<String, String> variables = buildBaseVariables(request, nights);
        variables.put("nights", String.valueOf(nights));
        return substituteVariables(template, variables);
    }

    /**
     * Build a user prompt for B&B search.
     */
    public String buildBBPrompt(String template, TravelRequest request) {
        long nights = calculateNights(request);
        Map<String, String> variables = buildBaseVariables(request, nights);
        variables.put("nights", String.valueOf(nights));
        return substituteVariables(template, variables);
    }

    /**
     * Build a user prompt for apartment search.
     */
    public String buildApartmentPrompt(String template, TravelRequest request) {
        long nights = calculateNights(request);
        Map<String, String> variables = buildBaseVariables(request, nights);
        variables.put("nights", String.valueOf(nights));
        variables.put("guestCount", String.valueOf(request.guestCount()));
        return substituteVariables(template, variables);
    }

    /**
     * Build a user prompt for hostel search.
     */
    public String buildHostelPrompt(String template, TravelRequest request) {
        long nights = calculateNights(request);
        Map<String, String> variables = buildBaseVariables(request, nights);
        variables.put("nights", String.valueOf(nights));
        return substituteVariables(template, variables);
    }

    /**
     * Build a user prompt for flight search.
     */
    public String buildFlightPrompt(String template, TravelRequest request) {
        Map<String, String> variables = buildBaseVariables(request, 0);
        variables.put("guestCount", String.valueOf(request.guestCount()));
        variables.put("maxBudget", String.format("%.2f", request.budgetMax()));
        variables.put("originCity", request.originCity() != null ? request.originCity() : "nearest major airport");
        return substituteVariables(template, variables);
    }

    /**
     * Build a user prompt for ground transport search.
     */
    public String buildCarBusPrompt(String template, TravelRequest request) {
        long days = calculateNights(request);
        Map<String, String> variables = buildBaseVariables(request, days);
        variables.put("days", String.valueOf(days));
        variables.put("guestCount", String.valueOf(request.guestCount()));
        variables.put("maxBudget", String.format("%.2f", request.budgetMax()));
        return substituteVariables(template, variables);
    }

    /** Build prompt for train search. */
    public String buildTrainPrompt(String template, TravelRequest request) {
        Map<String, String> variables = buildBaseVariables(request, calculateNights(request));
        variables.put("days", String.valueOf(calculateNights(request)));
        variables.put("guestCount", String.valueOf(request.guestCount()));
        variables.put("maxBudget", String.format("%.2f", request.budgetMax()));
        return substituteVariables(template, variables);
    }

    /** Build prompt for ferry search. */
    public String buildFerryPrompt(String template, TravelRequest request) {
        Map<String, String> variables = buildBaseVariables(request, calculateNights(request));
        variables.put("guestCount", String.valueOf(request.guestCount()));
        return substituteVariables(template, variables);
    }

    /** Build prompt for attractions search. */
    public String buildAttractionsPrompt(String template, TravelRequest request) {
        long nights = calculateNights(request);
        Map<String, String> variables = buildBaseVariables(request, nights);
        variables.put("nights", String.valueOf(nights));
        variables.put("interestTags", request.interestTags() != null ?
            String.join(", ", request.interestTags()) : "general sightseeing");
        return substituteVariables(template, variables);
    }

    /** Build prompt for weather forecast. */
    public String buildWeatherPrompt(String template, TravelRequest request) {
        Map<String, String> variables = buildBaseVariables(request, 0);
        return substituteVariables(template, variables);
    }

    /** Build prompt for currency info. */
    public String buildCurrencyPrompt(String template, TravelRequest request) {
        Map<String, String> variables = new HashMap<>();
        variables.put("destination", request.destination());
        return substituteVariables(template, variables);
    }

    /** Build prompt for review summary. */
    public String buildReviewPrompt(String template, TravelRequest request) {
        Map<String, String> variables = new HashMap<>();
        variables.put("destination", request.destination());
        return substituteVariables(template, variables);
    }

    /** Build prompt for country specialist. */
    public String buildCountrySpecialistPrompt(String template, TravelRequest request) {
        Map<String, String> variables = new HashMap<>();
        variables.put("destination", request.destination());
        variables.put("travelStyle", request.travelStyle());
        return substituteVariables(template, variables);
    }

    /**
     * Replace all {{variable}} placeholders in the template with values from the map.
     */
    private String substituteVariables(String template, Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            result = result.replace(placeholder, entry.getValue());
        }
        return result;
    }

    /**
     * Calculate number of nights from start and end dates.
     */
    private long calculateNights(TravelRequest request) {
        long nights = ChronoUnit.DAYS.between(request.startDate(), request.endDate());
        return nights > 0 ? nights : 1;
    }

    /**
     * Build base variables common to most prompts.
     */
    private Map<String, String> buildBaseVariables(TravelRequest request, long nights) {
        Map<String, String> vars = new HashMap<>();
        vars.put("destination", request.destination());
        vars.put("startDate", request.startDate().toString());
        vars.put("endDate", request.endDate().toString());
        vars.put("guestCount", String.valueOf(request.guestCount()));
        vars.put("budgetMin", String.format("%.2f", request.budgetMin()));
        vars.put("budgetMax", String.format("%.2f", request.budgetMax()));
        vars.put("travelStyle", request.travelStyle());
        vars.put("originCity", request.originCity() != null ? request.originCity() : "");
        vars.put("interestTags", request.interestTags() != null ?
            String.join(", ", request.interestTags()) : "");
        if (nights > 0) {
            vars.put("nights", String.valueOf(nights));
        }
        return vars;
    }
}
