package com.matoe.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests Jackson serialization/deserialization round-trip for all domain models.
 */
class DomainModelTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
    }

    // ── TravelRequest ────────────────────────────────────────────────────────

    @Test
    void travelRequest_roundTrip() throws Exception {
        TravelRequest original = new TravelRequest(
            "Paris",
            List.of("Paris", "Lyon"),
            LocalDate.of(2024, 6, 1),
            LocalDate.of(2024, 6, 10),
            0, 2, 1, List.of(7), 1,
            2000.0,
            5000.0,
            "standard",
            List.of("hotel", "bb"),
            List.of("flight"),
            List.of("food", "history"),
            "half-board",
            "anthropic/claude-3-5-sonnet",
            "lmstudio/llama-3-8b",
            "London",
            "session-abc-123"
        );

        String json = mapper.writeValueAsString(original);
        TravelRequest deserialized = mapper.readValue(json, TravelRequest.class);

        assertEquals("Paris", deserialized.destination());
        assertEquals(List.of("Paris", "Lyon"), deserialized.destinations());
        assertEquals(LocalDate.of(2024, 6, 1), deserialized.startDate());
        assertEquals(LocalDate.of(2024, 6, 10), deserialized.endDate());
        assertEquals(3, deserialized.guestCount());   // 2 adults + 1 child
        assertEquals(2, deserialized.adults());
        assertEquals(1, deserialized.children());
        assertEquals(List.of(7), deserialized.childrenAges());
        assertEquals(1, deserialized.rooms());
        assertEquals(2000.0, deserialized.budgetMin());
        assertEquals(5000.0, deserialized.budgetMax());
        assertEquals("standard", deserialized.travelStyle());
        assertEquals(List.of("hotel", "bb"), deserialized.accommodationTypes());
        assertEquals(List.of("flight"), deserialized.transportTypes());
        assertEquals(List.of("food", "history"), deserialized.interestTags());
        assertEquals("half-board", deserialized.mealPlan());
        assertEquals("anthropic/claude-3-5-sonnet", deserialized.orchestratorModel());
        assertEquals("lmstudio/llama-3-8b", deserialized.extractorModel());
        assertEquals("London", deserialized.originCity());
        assertEquals("session-abc-123", deserialized.sessionId());
    }

    @Test
    void travelRequest_nightsCalculation() {
        TravelRequest req = new TravelRequest(
            "Paris", null,
            LocalDate.of(2024, 6, 1), LocalDate.of(2024, 6, 10),
            2, 0, 0, null, 0,
            2000.0, 5000.0, null, null, null, null,
            null, null, null, null, null
        );
        assertEquals(9, req.nights());
    }

    @Test
    void travelRequest_defaultValues() {
        TravelRequest req = new TravelRequest(
            "Berlin", null,
            LocalDate.of(2024, 7, 1), LocalDate.of(2024, 7, 5),
            1, 0, 0, null, 0,
            500.0, 1500.0, null, null, null, null,
            null, null, null, null, null
        );
        // Compact constructor should fill in defaults
        assertEquals(List.of("Berlin"), req.destinations());
        assertEquals("standard", req.travelStyle());
        assertEquals(List.of("hotel"), req.accommodationTypes());
        assertEquals(List.of("flight"), req.transportTypes());
        assertEquals(List.of(), req.interestTags());
        assertEquals("", req.originCity());
    }

    // ── AccommodationOption (12-field version) ───────────────────────────────

    @Test
    void accommodationOption_roundTrip() throws Exception {
        AccommodationOption original = new AccommodationOption(
            "h-001", "hotel", "Grand Hotel Paris",
            150.0, 1350.0, 4.5, "1st Arrondissement",
            List.of("wifi", "pool", "spa"),
            "https://booking.com/grand-hotel",
            "standard",
            "browser",
            "https://images.example.com/grand-hotel.jpg"
        );

        String json = mapper.writeValueAsString(original);
        AccommodationOption deserialized = mapper.readValue(json, AccommodationOption.class);

        assertEquals("h-001", deserialized.id());
        assertEquals("hotel", deserialized.type());
        assertEquals("Grand Hotel Paris", deserialized.name());
        assertEquals(150.0, deserialized.pricePerNight());
        assertEquals(1350.0, deserialized.totalPrice());
        assertEquals(4.5, deserialized.rating());
        assertEquals("1st Arrondissement", deserialized.location());
        assertEquals(List.of("wifi", "pool", "spa"), deserialized.amenities());
        assertEquals("https://booking.com/grand-hotel", deserialized.bookingUrl());
        assertEquals("standard", deserialized.tier());
        assertEquals("browser", deserialized.source());
        assertEquals("https://images.example.com/grand-hotel.jpg", deserialized.imageUrl());
    }

    @Test
    void accommodationOption_backwardsCompatibleConstructor() {
        AccommodationOption option = new AccommodationOption(
            "h-002", "bb", "Cozy B&B", 80.0, 720.0, 4.2,
            "Montmartre", List.of("breakfast"), "https://example.com", "budget"
        );
        assertEquals("llm", option.source());
        assertEquals("", option.imageUrl());
    }

    // ── TransportOption (13-field version) ───────────────────────────────────

    @Test
    void transportOption_roundTrip() throws Exception {
        TransportOption original = new TransportOption(
            "t-001", "flight", "Air France",
            "2024-06-01T08:00", "2024-06-01T10:30",
            "2h30m", 250.0, 0,
            "https://airfrance.com/book",
            "standard",
            "api",
            "London", "Paris"
        );

        String json = mapper.writeValueAsString(original);
        TransportOption deserialized = mapper.readValue(json, TransportOption.class);

        assertEquals("t-001", deserialized.id());
        assertEquals("flight", deserialized.type());
        assertEquals("Air France", deserialized.provider());
        assertEquals("2024-06-01T08:00", deserialized.departureTime());
        assertEquals("2024-06-01T10:30", deserialized.arrivalTime());
        assertEquals("2h30m", deserialized.duration());
        assertEquals(250.0, deserialized.price());
        assertEquals(0, deserialized.stops());
        assertEquals("https://airfrance.com/book", deserialized.bookingUrl());
        assertEquals("standard", deserialized.tier());
        assertEquals("api", deserialized.source());
        assertEquals("London", deserialized.origin());
        assertEquals("Paris", deserialized.destination());
    }

    @Test
    void transportOption_backwardsCompatibleConstructor() {
        TransportOption option = new TransportOption(
            "t-002", "bus", "FlixBus", "2024-06-01T06:00", "2024-06-01T14:00",
            "8h", 40.0, 2, "https://flixbus.com", "budget"
        );
        assertEquals("llm", option.source());
        assertEquals("", option.origin());
        assertEquals("", option.destination());
    }

    // ── AttractionOption ─────────────────────────────────────────────────────

    @Test
    void attractionOption_roundTrip() throws Exception {
        AttractionOption original = new AttractionOption(
            "a-001", "Louvre Museum",
            "World-famous art museum housing the Mona Lisa",
            "museum", 17.0, "3h", 4.8,
            "Rue de Rivoli, Paris",
            "https://louvre.fr/tickets",
            "standard", "llm",
            List.of("art", "history", "culture")
        );

        String json = mapper.writeValueAsString(original);
        AttractionOption deserialized = mapper.readValue(json, AttractionOption.class);

        assertEquals("a-001", deserialized.id());
        assertEquals("Louvre Museum", deserialized.name());
        assertEquals("World-famous art museum housing the Mona Lisa", deserialized.description());
        assertEquals("museum", deserialized.category());
        assertEquals(17.0, deserialized.price());
        assertEquals("3h", deserialized.duration());
        assertEquals(4.8, deserialized.rating());
        assertEquals("Rue de Rivoli, Paris", deserialized.location());
        assertEquals("https://louvre.fr/tickets", deserialized.bookingUrl());
        assertEquals("standard", deserialized.tier());
        assertEquals("llm", deserialized.source());
        assertEquals(List.of("art", "history", "culture"), deserialized.tags());
    }

    // ── ItineraryDay ─────────────────────────────────────────────────────────

    @Test
    void itineraryDay_roundTrip() throws Exception {
        ItineraryDay original = new ItineraryDay(
            1, "2024-06-01", "Arrival & Montmartre Exploration",
            "Settle in and explore the artistic heart of Paris",
            List.of("Check into hotel", "Walk to Sacre-Coeur"),
            List.of("Lunch at local bistro", "Visit Place du Tertre"),
            List.of("Dinner at Le Consulat", "Seine river walk"),
            List.of("Cafe de Flore (breakfast)", "Le Consulat (dinner)"),
            "Take Metro Line 2 to Anvers",
            120.0
        );

        String json = mapper.writeValueAsString(original);
        ItineraryDay deserialized = mapper.readValue(json, ItineraryDay.class);

        assertEquals(1, deserialized.dayNumber());
        assertEquals("2024-06-01", deserialized.date());
        assertEquals("Arrival & Montmartre Exploration", deserialized.title());
        assertEquals("Settle in and explore the artistic heart of Paris", deserialized.summary());
        assertEquals(2, deserialized.morningActivities().size());
        assertEquals(2, deserialized.afternoonActivities().size());
        assertEquals(2, deserialized.eveningActivities().size());
        assertEquals(2, deserialized.meals().size());
        assertEquals("Take Metro Line 2 to Anvers", deserialized.transportNotes());
        assertEquals(120.0, deserialized.estimatedDayCost());
    }

    // ── ItineraryVariant ─────────────────────────────────────────────────────

    @Test
    void itineraryVariant_roundTrip() throws Exception {
        AccommodationOption accom = new AccommodationOption(
            "h-001", "hotel", "Budget Inn", 60.0, 540.0, 3.5,
            "Suburbs", List.of("wifi"), "https://example.com", "budget"
        );
        TransportOption trans = new TransportOption(
            "t-001", "bus", "FlixBus", "06:00", "14:00",
            "8h", 40.0, 2, "https://flixbus.com", "budget"
        );
        AttractionOption attract = new AttractionOption(
            "a-001", "Free Walking Tour", "Guided city walk",
            "tour", 0.0, "2h", 4.5, "City Center",
            "https://example.com", "budget", "llm", List.of("walking", "free")
        );
        ItineraryDay day = new ItineraryDay(
            1, "2024-06-01", "Day 1", "Arrival",
            List.of("Arrive"), List.of("Explore"), List.of("Rest"),
            List.of("Street food"), "Walk", 50.0
        );

        ItineraryVariant original = new ItineraryVariant(
            "budget", 580.0,
            List.of(accom), List.of(trans), List.of(attract),
            List.of(day),
            List.of("Best value for solo travellers", "Authentic local experience"),
            "Fewer amenities, longer transit times"
        );

        String json = mapper.writeValueAsString(original);
        ItineraryVariant deserialized = mapper.readValue(json, ItineraryVariant.class);

        assertEquals("budget", deserialized.tier());
        assertEquals(580.0, deserialized.totalEstimatedCost());
        assertEquals(1, deserialized.accommodations().size());
        assertEquals("Budget Inn", deserialized.accommodations().get(0).name());
        assertEquals(1, deserialized.transport().size());
        assertEquals(1, deserialized.attractions().size());
        assertEquals(1, deserialized.dayByDay().size());
        assertEquals(2, deserialized.highlights().size());
        assertEquals("Fewer amenities, longer transit times", deserialized.tradeoffs());
    }

    // ── UnforgettableItinerary (full constructor) ────────────────────────────

    @Test
    void unforgettableItinerary_fullConstructor_roundTrip() throws Exception {
        AccommodationOption accom = new AccommodationOption(
            "h-001", "hotel", "Test Hotel", 100.0, 900.0, 4.0,
            "Central Paris", List.of("wifi"), "https://example.com", "standard",
            "browser", "https://images.example.com/hotel.jpg"
        );
        TransportOption trans = new TransportOption(
            "t-001", "flight", "Air France", "08:00", "10:30",
            "2h30m", 250.0, 0, "https://airfrance.com", "standard",
            "api", "London", "Paris"
        );
        AttractionOption attract = new AttractionOption(
            "a-001", "Eiffel Tower", "Iconic landmark",
            "landmark", 25.0, "2h", 4.9, "Champ de Mars",
            "https://toureiffel.paris", "standard", "llm", List.of("iconic")
        );
        ItineraryDay day = new ItineraryDay(
            1, "2024-06-01", "Day 1", "Arrival day",
            List.of("Arrive"), List.of("Eiffel Tower"), List.of("Dinner cruise"),
            List.of("Le Jules Verne"), "Taxi from airport", 200.0
        );
        ItineraryVariant variant = new ItineraryVariant(
            "standard", 3500.0,
            List.of(accom), List.of(trans), List.of(attract),
            List.of(day), List.of("Balanced"), "Good compromise"
        );

        Map<String, Object> regionInsights = Map.of("language", "French", "currency", "EUR");
        Map<String, Object> weather = Map.of("averageTemp", 22, "precipitation", "low");
        Map<String, Object> currency = Map.of("currencyCode", "EUR", "exchangeRate", 1.0);

        UnforgettableItinerary original = new UnforgettableItinerary(
            "itin-001", "Paris", "2024-06-01", "2024-06-10",
            2, regionInsights,
            List.of(accom), List.of(trans), List.of(attract),
            List.of(variant), 3500.0,
            weather, currency,
            LocalDateTime.of(2024, 5, 15, 10, 30, 0)
        );

        String json = mapper.writeValueAsString(original);
        UnforgettableItinerary deserialized = mapper.readValue(json, UnforgettableItinerary.class);

        assertEquals("itin-001", deserialized.id());
        assertEquals("Paris", deserialized.destination());
        assertEquals("2024-06-01", deserialized.startDate());
        assertEquals("2024-06-10", deserialized.endDate());
        assertEquals(2, deserialized.guestCount());
        assertEquals("French", deserialized.regionInsights().get("language"));
        assertEquals(1, deserialized.accommodations().size());
        assertEquals(1, deserialized.transport().size());
        assertEquals(1, deserialized.attractions().size());
        assertEquals(1, deserialized.variants().size());
        assertEquals("standard", deserialized.variants().get(0).tier());
        assertEquals(3500.0, deserialized.totalEstimatedCost());
        assertEquals(22, deserialized.weatherForecast().get("averageTemp"));
        assertEquals("EUR", deserialized.currencyInfo().get("currencyCode"));
        assertEquals(LocalDateTime.of(2024, 5, 15, 10, 30, 0), deserialized.createdAt());
    }

    @Test
    void unforgettableItinerary_backwardsCompatibleConstructor() {
        UnforgettableItinerary itinerary = new UnforgettableItinerary(
            "itin-002", "Rome", "2024-07-01", "2024-07-07",
            3, Map.of("language", "Italian"),
            List.of(), List.of(),
            2500.0, LocalDateTime.of(2024, 6, 1, 12, 0, 0)
        );

        assertEquals("itin-002", itinerary.id());
        assertEquals(List.of(), itinerary.attractions());
        assertEquals(List.of(), itinerary.variants());
        assertEquals(Map.of(), itinerary.weatherForecast());
        assertEquals(Map.of(), itinerary.currencyInfo());
    }
}
