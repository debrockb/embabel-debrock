package com.matoe.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.matoe.domain.*;
import com.matoe.service.DynamicPromptService;
import com.matoe.service.LlmCostTrackingService;
import com.matoe.service.LlmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests the OrchestratorAgent's fallback variant building logic.
 * The buildFallbackVariants method is package-private for testability.
 */
class OrchestratorAgentTest {

    private OrchestratorAgent orchestratorAgent;

    @BeforeEach
    void setUp() {
        // Create mocks for dependencies that we don't exercise in these tests
        LlmService llmService = mock(LlmService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        DynamicPromptService dynamicPromptService = mock(DynamicPromptService.class);
        LlmCostTrackingService costTracker = mock(LlmCostTrackingService.class);

        orchestratorAgent = new OrchestratorAgent(
            llmService, objectMapper, dynamicPromptService, costTracker
        );
    }

    private TravelRequest createTestRequest() {
        return new TravelRequest(
            "Paris",
            List.of("Paris"),
            LocalDate.of(2024, 6, 1),
            LocalDate.of(2024, 6, 4),   // 3 nights
            2,
            1000.0,
            3000.0,
            "standard",
            List.of("hotel"),
            List.of("flight"),
            List.of(),
            "anthropic/claude-3-5-sonnet",
            "lmstudio/llama-3-8b",
            "London",
            "test-session"
        );
    }

    private List<AccommodationOption> createTestAccommodations() {
        return List.of(
            new AccommodationOption("h-1", "hotel", "Budget Hotel", 50.0, 150.0, 3.0,
                "Suburb", List.of("wifi"), "https://example.com/1", "budget",
                "llm", ""),
            new AccommodationOption("h-2", "hotel", "Standard Hotel", 120.0, 360.0, 4.0,
                "City Center", List.of("wifi", "pool"), "https://example.com/2", "standard",
                "llm", ""),
            new AccommodationOption("h-3", "hotel", "Luxury Palace", 350.0, 1050.0, 4.8,
                "Champs-Elysees", List.of("wifi", "pool", "spa", "concierge"),
                "https://example.com/3", "luxury", "browser", "https://img.example.com/lux.jpg")
        );
    }

    private List<TransportOption> createTestTransport() {
        return List.of(
            new TransportOption("t-1", "flight", "Budget Air", "06:00", "08:00",
                "2h", 80.0, 0, "https://example.com/f1", "budget",
                "llm", "London", "Paris"),
            new TransportOption("t-2", "flight", "Air France", "10:00", "12:30",
                "2h30m", 200.0, 0, "https://example.com/f2", "standard",
                "api", "London", "Paris"),
            new TransportOption("t-3", "flight", "First Class Air", "14:00", "16:00",
                "2h", 600.0, 0, "https://example.com/f3", "luxury",
                "api", "London", "Paris")
        );
    }

    private List<AttractionOption> createTestAttractions() {
        return List.of(
            new AttractionOption("a-1", "Free Walking Tour", "City walk",
                "tour", 0.0, "2h", 4.5, "Center",
                "https://example.com/a1", "budget", "llm", List.of("free")),
            new AttractionOption("a-2", "Louvre Museum", "World-famous museum",
                "museum", 17.0, "3h", 4.8, "Rivoli",
                "https://example.com/a2", "standard", "llm", List.of("art")),
            new AttractionOption("a-3", "Private Wine Tasting", "Exclusive experience",
                "food", 150.0, "4h", 4.9, "Montmartre",
                "https://example.com/a3", "luxury", "llm", List.of("wine"))
        );
    }

    // ── buildFallbackVariants ────────────────────────────────────────────────

    @Test
    void buildFallbackVariants_produces3Variants() {
        TravelRequest request = createTestRequest();
        List<AccommodationOption> accommodations = createTestAccommodations();
        List<TransportOption> transport = createTestTransport();
        List<AttractionOption> attractions = createTestAttractions();

        List<ItineraryVariant> variants = orchestratorAgent.buildFallbackVariants(
            accommodations, transport, attractions, request
        );

        assertEquals(3, variants.size(), "Should produce exactly 3 variants");
    }

    @Test
    void buildFallbackVariants_hasBudgetStandardLuxuryTiers() {
        TravelRequest request = createTestRequest();
        List<ItineraryVariant> variants = orchestratorAgent.buildFallbackVariants(
            createTestAccommodations(), createTestTransport(), createTestAttractions(), request
        );

        assertEquals("budget", variants.get(0).tier());
        assertEquals("standard", variants.get(1).tier());
        assertEquals("luxury", variants.get(2).tier());
    }

    @Test
    void buildFallbackVariants_eachVariantHasDayByDay() {
        TravelRequest request = createTestRequest();
        List<ItineraryVariant> variants = orchestratorAgent.buildFallbackVariants(
            createTestAccommodations(), createTestTransport(), createTestAttractions(), request
        );

        for (ItineraryVariant variant : variants) {
            assertNotNull(variant.dayByDay(), "Variant " + variant.tier() + " should have dayByDay");
            assertFalse(variant.dayByDay().isEmpty(),
                "Variant " + variant.tier() + " dayByDay should not be empty");
            // 3 nights = 3 days in the default day generator
            assertEquals(3, variant.dayByDay().size(),
                "Variant " + variant.tier() + " should have 3 days (matching nights)");
        }
    }

    @Test
    void buildFallbackVariants_budgetVariantFiltersCorrectly() {
        TravelRequest request = createTestRequest();
        List<ItineraryVariant> variants = orchestratorAgent.buildFallbackVariants(
            createTestAccommodations(), createTestTransport(), createTestAttractions(), request
        );

        ItineraryVariant budget = variants.get(0);
        assertEquals(1, budget.accommodations().size());
        assertEquals("Budget Hotel", budget.accommodations().get(0).name());
        assertEquals(1, budget.transport().size());
        assertEquals("Budget Air", budget.transport().get(0).provider());
        assertEquals(1, budget.attractions().size());
        assertEquals("Free Walking Tour", budget.attractions().get(0).name());
    }

    @Test
    void buildFallbackVariants_standardVariantFiltersCorrectly() {
        TravelRequest request = createTestRequest();
        List<ItineraryVariant> variants = orchestratorAgent.buildFallbackVariants(
            createTestAccommodations(), createTestTransport(), createTestAttractions(), request
        );

        ItineraryVariant standard = variants.get(1);
        assertEquals(1, standard.accommodations().size());
        assertEquals("Standard Hotel", standard.accommodations().get(0).name());
        assertEquals(1, standard.transport().size());
        assertEquals("Air France", standard.transport().get(0).provider());
    }

    @Test
    void buildFallbackVariants_luxuryVariantFiltersCorrectly() {
        TravelRequest request = createTestRequest();
        List<ItineraryVariant> variants = orchestratorAgent.buildFallbackVariants(
            createTestAccommodations(), createTestTransport(), createTestAttractions(), request
        );

        ItineraryVariant luxury = variants.get(2);
        assertEquals(1, luxury.accommodations().size());
        assertEquals("Luxury Palace", luxury.accommodations().get(0).name());
        assertEquals(1, luxury.transport().size());
        assertEquals("First Class Air", luxury.transport().get(0).provider());
    }

    @Test
    void buildFallbackVariants_costCalculation() {
        TravelRequest request = createTestRequest();
        List<ItineraryVariant> variants = orchestratorAgent.buildFallbackVariants(
            createTestAccommodations(), createTestTransport(), createTestAttractions(), request
        );

        // Budget: hotel 150 + flight 80 + attraction 0 = 230
        assertEquals(230.0, variants.get(0).totalEstimatedCost(), 0.01);

        // Standard: hotel 360 + flight 200 + attraction 17 = 577
        assertEquals(577.0, variants.get(1).totalEstimatedCost(), 0.01);

        // Luxury: hotel 1050 + flight 600 + attraction 150 = 1800
        assertEquals(1800.0, variants.get(2).totalEstimatedCost(), 0.01);
    }

    @Test
    void buildFallbackVariants_highlightsAndTradeoffs() {
        TravelRequest request = createTestRequest();
        List<ItineraryVariant> variants = orchestratorAgent.buildFallbackVariants(
            createTestAccommodations(), createTestTransport(), createTestAttractions(), request
        );

        for (ItineraryVariant variant : variants) {
            assertNotNull(variant.highlights());
            assertFalse(variant.highlights().isEmpty(),
                "Variant " + variant.tier() + " should have at least one highlight");

            assertNotNull(variant.tradeoffs());
            assertEquals("Fallback variant — LLM synthesis unavailable", variant.tradeoffs());
        }
    }

    @Test
    void buildFallbackVariants_withEmptyAccommodations() {
        TravelRequest request = createTestRequest();
        List<ItineraryVariant> variants = orchestratorAgent.buildFallbackVariants(
            List.of(), createTestTransport(), createTestAttractions(), request
        );

        assertEquals(3, variants.size());
        for (ItineraryVariant variant : variants) {
            assertTrue(variant.accommodations().isEmpty(),
                "Variant " + variant.tier() + " should have no accommodations");
        }
    }

    @Test
    void buildFallbackVariants_withNullAttractions() {
        TravelRequest request = createTestRequest();
        List<ItineraryVariant> variants = orchestratorAgent.buildFallbackVariants(
            createTestAccommodations(), createTestTransport(), null, request
        );

        assertEquals(3, variants.size());
        for (ItineraryVariant variant : variants) {
            assertTrue(variant.attractions().isEmpty(),
                "Variant " + variant.tier() + " should have empty attractions when null input");
        }
    }

    // ── generateDefaultDays ──────────────────────────────────────────────────

    @Test
    void generateDefaultDays_matchesNightsCount() {
        TravelRequest request = createTestRequest(); // 3 nights
        List<ItineraryDay> days = orchestratorAgent.generateDefaultDays(request);

        assertEquals(3, days.size());
        assertEquals(1, days.get(0).dayNumber());
        assertEquals(2, days.get(1).dayNumber());
        assertEquals(3, days.get(2).dayNumber());
    }

    @Test
    void generateDefaultDays_firstDayIsArrival() {
        TravelRequest request = createTestRequest();
        List<ItineraryDay> days = orchestratorAgent.generateDefaultDays(request);

        assertEquals("Arrival Day", days.get(0).title());
    }

    @Test
    void generateDefaultDays_lastDayIsDeparture() {
        TravelRequest request = createTestRequest();
        List<ItineraryDay> days = orchestratorAgent.generateDefaultDays(request);

        assertEquals("Departure Day", days.get(days.size() - 1).title());
    }

    @Test
    void generateDefaultDays_datesAreSequential() {
        TravelRequest request = createTestRequest();
        List<ItineraryDay> days = orchestratorAgent.generateDefaultDays(request);

        assertEquals("2024-06-01", days.get(0).date());
        assertEquals("2024-06-02", days.get(1).date());
        assertEquals("2024-06-03", days.get(2).date());
    }
}
