package com.matoe.service;

import com.matoe.domain.TravelRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests PromptTemplateService variable substitution for various agent prompts.
 */
class PromptTemplateServiceTest {

    private PromptTemplateService service;
    private TravelRequest request;

    private static final Pattern UNRESOLVED_VARIABLE = Pattern.compile("\\{\\{\\w+}}");

    @BeforeEach
    void setUp() {
        service = new PromptTemplateService();
        request = new TravelRequest(
            "Paris",
            List.of("Paris"),
            LocalDate.of(2024, 6, 1),
            LocalDate.of(2024, 6, 10),
            0, 2, 0, null, 1,
            2000.0,
            5000.0,
            "standard",
            List.of("hotel", "bb"),
            List.of("flight"),
            List.of("food", "history"),
            "anthropic/claude-3-5-sonnet",
            "lmstudio/llama-3-8b",
            "London",
            "session-test-001"
        );
    }

    // ── Hotel prompt ─────────────────────────────────────────────────────────

    @Test
    void buildHotelPrompt_substitutesDestination() {
        String template = "Find hotels in {{destination}} for {{guestCount}} guests, {{nights}} nights.";
        String result = service.buildHotelPrompt(template, request);
        assertTrue(result.contains("Paris"), "Should contain destination 'Paris'");
    }

    @Test
    void buildHotelPrompt_substitutesNights() {
        String template = "Find hotels in {{destination}} for {{nights}} nights.";
        String result = service.buildHotelPrompt(template, request);
        // 9 nights between June 1 and June 10
        assertTrue(result.contains("9"), "Should contain nights count '9'");
    }

    @Test
    void buildHotelPrompt_substitutesGuestCount() {
        String template = "Hotels for {{guestCount}} guests in {{destination}}.";
        String result = service.buildHotelPrompt(template, request);
        assertTrue(result.contains("2"), "Should contain guest count '2'");
    }

    @Test
    void buildHotelPrompt_noUnresolvedVariables() {
        String template = "Find hotels in {{destination}} for {{guestCount}} guests, " +
            "{{startDate}} to {{endDate}} ({{nights}} nights). " +
            "Budget: {{budgetMin}}-{{budgetMax}}. Style: {{travelStyle}}.";
        String result = service.buildHotelPrompt(template, request);
        assertFalse(UNRESOLVED_VARIABLE.matcher(result).find(),
            "No unresolved {{variable}} placeholders should remain. Got: " + result);
    }

    @Test
    void buildHotelPrompt_budgetValues() {
        String template = "Budget: {{budgetMin}} to {{budgetMax}}.";
        String result = service.buildHotelPrompt(template, request);
        assertTrue(result.contains("2000.00"), "Should contain budget min");
        assertTrue(result.contains("5000.00"), "Should contain budget max");
    }

    // ── Flight prompt ────────────────────────────────────────────────────────

    @Test
    void buildFlightPrompt_substitutesDestination() {
        String template = "Flights to {{destination}} for {{guestCount}} passengers. Max: {{maxBudget}}.";
        String result = service.buildFlightPrompt(template, request);
        assertTrue(result.contains("Paris"), "Should contain destination 'Paris'");
    }

    @Test
    void buildFlightPrompt_substitutesGuestCount() {
        String template = "Flights for {{guestCount}} passengers.";
        String result = service.buildFlightPrompt(template, request);
        assertTrue(result.contains("2"), "Should contain guest count '2'");
    }

    @Test
    void buildFlightPrompt_substitutesMaxBudget() {
        String template = "Max budget: {{maxBudget}}.";
        String result = service.buildFlightPrompt(template, request);
        assertTrue(result.contains("5000.00"), "Should contain max budget");
    }

    @Test
    void buildFlightPrompt_noUnresolvedVariablesForSupportedPlaceholders() {
        // Note: buildFlightPrompt does NOT add originCity to the variable map,
        // so {{originCity}} would remain unresolved. We test only the variables it supports.
        String template = "Flights to {{destination}} for {{guestCount}} passengers, " +
            "departing {{startDate}} returning {{endDate}}. Max: {{maxBudget}}.";
        String result = service.buildFlightPrompt(template, request);
        assertFalse(UNRESOLVED_VARIABLE.matcher(result).find(),
            "No unresolved {{variable}} placeholders should remain. Got: " + result);
    }

    // ── B&B prompt ───────────────────────────────────────────────────────────

    @Test
    void buildBBPrompt_substitutesVariables() {
        String template = "Find B&Bs in {{destination}} for {{guestCount}} guests, {{nights}} nights.";
        String result = service.buildBBPrompt(template, request);
        assertTrue(result.contains("Paris"));
        assertTrue(result.contains("2"));
        assertTrue(result.contains("9"));
    }

    @Test
    void buildBBPrompt_noUnresolvedVariables() {
        String template = "B&Bs in {{destination}}, {{startDate}} to {{endDate}}, " +
            "{{guestCount}} guests, {{nights}} nights. Style: {{travelStyle}}.";
        String result = service.buildBBPrompt(template, request);
        assertFalse(UNRESOLVED_VARIABLE.matcher(result).find(),
            "No unresolved {{variable}} placeholders should remain. Got: " + result);
    }

    // ── Apartment prompt ─────────────────────────────────────────────────────

    @Test
    void buildApartmentPrompt_substitutesGuestCount() {
        String template = "Apartments for {{guestCount}} guests in {{destination}}, {{nights}} nights.";
        String result = service.buildApartmentPrompt(template, request);
        assertTrue(result.contains("2"));
        assertTrue(result.contains("Paris"));
        assertTrue(result.contains("9"));
    }

    // ── Country specialist prompt ────────────────────────────────────────────

    @Test
    void buildCountrySpecialistPrompt_substitutesVariables() {
        String template = "Travel intelligence for {{destination}}, style: {{travelStyle}}.";
        String result = service.buildCountrySpecialistPrompt(template, request);
        assertTrue(result.contains("Paris"));
        assertTrue(result.contains("standard"));
    }

    @Test
    void buildCountrySpecialistPrompt_noUnresolvedVariables() {
        String template = "Expert for {{destination}}, style: {{travelStyle}}.";
        String result = service.buildCountrySpecialistPrompt(template, request);
        assertFalse(UNRESOLVED_VARIABLE.matcher(result).find(),
            "No unresolved {{variable}} placeholders should remain. Got: " + result);
    }

    // ── CarBus prompt ────────────────────────────────────────────────────────

    @Test
    void buildCarBusPrompt_substitutesVariables() {
        String template = "Ground transport in {{destination}} for {{days}} days, " +
            "{{guestCount}} passengers. Max: {{maxBudget}}.";
        String result = service.buildCarBusPrompt(template, request);
        assertTrue(result.contains("Paris"));
        assertTrue(result.contains("9"));    // days
        assertTrue(result.contains("2"));    // guestCount
        assertTrue(result.contains("5000.00")); // maxBudget
    }

    @Test
    void buildCarBusPrompt_noUnresolvedVariables() {
        String template = "Transport in {{destination}}, {{startDate}} to {{endDate}}, " +
            "{{days}} days, {{guestCount}} pax, max {{maxBudget}}. Style: {{travelStyle}}.";
        String result = service.buildCarBusPrompt(template, request);
        assertFalse(UNRESOLVED_VARIABLE.matcher(result).find(),
            "No unresolved {{variable}} placeholders should remain. Got: " + result);
    }

    // ── Hostel prompt ────────────────────────────────────────────────────────

    @Test
    void buildHostelPrompt_substitutesVariables() {
        String template = "Hostels in {{destination}} for {{nights}} nights.";
        String result = service.buildHostelPrompt(template, request);
        assertTrue(result.contains("Paris"));
        assertTrue(result.contains("9"));
    }

    // ── Edge cases ───────────────────────────────────────────────────────────

    @Test
    void singleNight_calculatedCorrectly() {
        TravelRequest oneNight = new TravelRequest(
            "Berlin", null,
            LocalDate.of(2024, 7, 1), LocalDate.of(2024, 7, 2),
            1, 0, 0, null, 0,
            100.0, 300.0, null, null, null, null,
            null, null, null, null
        );
        String template = "Stay for {{nights}} nights in {{destination}}.";
        String result = service.buildHotelPrompt(template, oneNight);
        assertTrue(result.contains("1"), "Should be 1 night");
        assertTrue(result.contains("Berlin"));
    }

    @Test
    void sameDayTrip_defaultsToOneNight() {
        TravelRequest sameDay = new TravelRequest(
            "Munich", null,
            LocalDate.of(2024, 7, 1), LocalDate.of(2024, 7, 1),
            1, 0, 0, null, 0,
            50.0, 200.0, null, null, null, null,
            null, null, null, null
        );
        String template = "Stay for {{nights}} nights.";
        String result = service.buildHotelPrompt(template, sameDay);
        // Same day → 0 nights → clamped to 1
        assertTrue(result.contains("1"), "Same-day trip should default to 1 night");
    }
}
