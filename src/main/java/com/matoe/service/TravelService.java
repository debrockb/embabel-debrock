package com.matoe.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matoe.agents.*;
import com.matoe.domain.*;
import com.matoe.entity.ItineraryEntity;
import com.matoe.repository.ItineraryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Core orchestration service.
 * Dispatches all specialist agents in parallel using Java Virtual Threads,
 * streams real-time progress to the frontend via SSE, persists results,
 * and returns the final synthesised itinerary.
 */
@Service
public class TravelService {

    private static final Logger log = LoggerFactory.getLogger(TravelService.class);

    private final OrchestratorAgent orchestratorAgent;
    private final HotelAgent hotelAgent;
    private final BBAgent bbAgent;
    private final ApartmentAgent apartmentAgent;
    private final HostelAgent hostelAgent;
    private final FlightAgent flightAgent;
    private final CarBusAgent carBusAgent;
    private final TrainAgent trainAgent;
    private final FerryAgent ferryAgent;
    private final CountrySpecialistAgent countrySpecialistAgent;
    private final AttractionsAgent attractionsAgent;
    private final WeatherAgent weatherAgent;
    private final CurrencyAgent currencyAgent;
    private final ReviewSummaryAgent reviewSummaryAgent;
    private final AgentProgressService progressService;
    private final ItineraryRepository repository;
    private final ObjectMapper objectMapper;

    public TravelService(
            OrchestratorAgent orchestratorAgent,
            HotelAgent hotelAgent, BBAgent bbAgent,
            ApartmentAgent apartmentAgent, HostelAgent hostelAgent,
            FlightAgent flightAgent, CarBusAgent carBusAgent,
            TrainAgent trainAgent, FerryAgent ferryAgent,
            CountrySpecialistAgent countrySpecialistAgent,
            AttractionsAgent attractionsAgent,
            WeatherAgent weatherAgent, CurrencyAgent currencyAgent,
            ReviewSummaryAgent reviewSummaryAgent,
            AgentProgressService progressService,
            ItineraryRepository repository,
            ObjectMapper objectMapper) {
        this.orchestratorAgent = orchestratorAgent;
        this.hotelAgent = hotelAgent;
        this.bbAgent = bbAgent;
        this.apartmentAgent = apartmentAgent;
        this.hostelAgent = hostelAgent;
        this.flightAgent = flightAgent;
        this.carBusAgent = carBusAgent;
        this.trainAgent = trainAgent;
        this.ferryAgent = ferryAgent;
        this.countrySpecialistAgent = countrySpecialistAgent;
        this.attractionsAgent = attractionsAgent;
        this.weatherAgent = weatherAgent;
        this.currencyAgent = currencyAgent;
        this.reviewSummaryAgent = reviewSummaryAgent;
        this.progressService = progressService;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    // ── public API ────────────────────────────────────────────────────────────

    public UnforgettableItinerary planTrip(TravelRequest request, String sessionId) {
        boolean live = sessionId != null && !sessionId.isBlank();

        emit(live, sessionId, "Orchestrator", "deployed", 5, "Initialising agent swarm...");

        // ── Phase 1: parallel intelligence gathering ──────────────────────────
        CompletableFuture<Map<String, Object>> insightsFuture = CompletableFuture.supplyAsync(() -> {
            emit(live, sessionId, "Country Specialist", "searching", 15, "Researching destination...");
            Map<String, Object> result = countrySpecialistAgent.gatherRegionalInsights(
                request.destination(), request.orchestratorModel());
            emit(live, sessionId, "Country Specialist", "completed", 100, "Insights ready");
            return result;
        });

        CompletableFuture<Map<String, Object>> weatherFuture = CompletableFuture.supplyAsync(() -> {
            emit(live, sessionId, "Weather Agent", "searching", 15, "Checking weather...");
            Map<String, Object> result = weatherAgent.getWeatherForecast(request);
            emit(live, sessionId, "Weather Agent", "completed", 100, "Weather data ready");
            return result;
        });

        CompletableFuture<Map<String, Object>> currencyFuture = CompletableFuture.supplyAsync(() -> {
            emit(live, sessionId, "Currency Agent", "searching", 15, "Checking currency...");
            Map<String, Object> result = currencyAgent.getCurrencyInfo(request);
            emit(live, sessionId, "Currency Agent", "completed", 100, "Currency info ready");
            return result;
        });

        CompletableFuture<Map<String, Object>> reviewFuture = CompletableFuture.supplyAsync(() -> {
            emit(live, sessionId, "Review Agent", "searching", 15, "Aggregating reviews...");
            Map<String, Object> result = reviewSummaryAgent.getReviewSummary(request);
            emit(live, sessionId, "Review Agent", "completed", 100, "Reviews summarised");
            return result;
        });

        // ── Phase 2: parallel accommodation + transport + attractions ─────────
        CompletableFuture<List<AccommodationOption>> accommodationFuture =
            CompletableFuture.supplyAsync(() -> searchAccommodations(request, sessionId, live));

        CompletableFuture<List<TransportOption>> transportFuture =
            CompletableFuture.supplyAsync(() -> searchTransport(request, sessionId, live));

        CompletableFuture<List<AttractionOption>> attractionsFuture =
            CompletableFuture.supplyAsync(() -> {
                emit(live, sessionId, "Attractions Agent", "searching", 20, "Finding attractions...");
                List<AttractionOption> result = attractionsAgent.searchAttractions(request);
                emit(live, sessionId, "Attractions Agent", "completed", 100,
                    result.size() + " attractions found");
                return result;
            });

        // Wait for all parallel phases
        CompletableFuture.allOf(
            insightsFuture, weatherFuture, currencyFuture, reviewFuture,
            accommodationFuture, transportFuture, attractionsFuture
        ).join();

        List<AccommodationOption> accommodations = accommodationFuture.join();
        List<TransportOption> transport = transportFuture.join();
        List<AttractionOption> attractions = attractionsFuture.join();
        Map<String, Object> insights = insightsFuture.join();
        Map<String, Object> weather = weatherFuture.join();
        Map<String, Object> currency = currencyFuture.join();
        Map<String, Object> reviews = reviewFuture.join();

        // Merge review data into insights
        if (reviews != null && !reviews.isEmpty()) {
            insights = new HashMap<>(insights);
            insights.put("reviewSummary", reviews);
        }

        // ── Phase 3: synthesise with orchestrator LLM ────────────────────────
        emit(live, sessionId, "Orchestrator", "analyzing", 90, "Synthesising itinerary...");
        UnforgettableItinerary itinerary = orchestratorAgent.synthesizeItinerary(
            request, accommodations, transport, attractions, insights, weather, currency);

        // ── Phase 4: persist ──────────────────────────────────────────────────
        save(itinerary);

        emit(live, sessionId, "Orchestrator", "completed", 100, "Itinerary ready!");
        progressService.complete(sessionId);

        return itinerary;
    }

    public List<UnforgettableItinerary> getAllItineraries() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
            .map(this::toItinerary).collect(Collectors.toList());
    }

    public List<UnforgettableItinerary> searchItineraries(String destination) {
        return repository.findByDestinationContainingIgnoreCaseOrderByCreatedAtDesc(destination)
            .stream().map(this::toItinerary).collect(Collectors.toList());
    }

    // ── private: accommodation search ─────────────────────────────────────────

    private List<AccommodationOption> searchAccommodations(
            TravelRequest request, String sessionId, boolean live) {

        List<AccommodationOption> results = new ArrayList<>();
        List<CompletableFuture<List<AccommodationOption>>> futures = new ArrayList<>();

        if (request.accommodationTypes().contains("hotel")) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                emit(live, sessionId, "Hotel Agent", "searching", 20, "Searching hotels...");
                List<AccommodationOption> r = hotelAgent.searchHotels(request);
                emit(live, sessionId, "Hotel Agent", "completed", 100, r.size() + " hotels found");
                return r;
            }));
        }

        if (request.accommodationTypes().contains("bb")) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                emit(live, sessionId, "B&B Agent", "searching", 20, "Searching B&Bs...");
                List<AccommodationOption> r = bbAgent.searchBBs(request);
                emit(live, sessionId, "B&B Agent", "completed", 100, r.size() + " B&Bs found");
                return r;
            }));
        }

        if (request.accommodationTypes().contains("apartment")) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                emit(live, sessionId, "Apartment Agent", "searching", 20, "Searching apartments...");
                List<AccommodationOption> r = apartmentAgent.searchApartments(request);
                emit(live, sessionId, "Apartment Agent", "completed", 100, r.size() + " apartments found");
                return r;
            }));
        }

        if (request.accommodationTypes().contains("hostel")) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                emit(live, sessionId, "Hostel Agent", "searching", 20, "Searching hostels...");
                List<AccommodationOption> r = hostelAgent.searchHostels(request);
                emit(live, sessionId, "Hostel Agent", "completed", 100, r.size() + " hostels found");
                return r;
            }));
        }

        futures.forEach(f -> results.addAll(f.join()));
        return tierAccommodations(results, request);
    }

    // ── private: transport search ─────────────────────────────────────────────

    private List<TransportOption> searchTransport(
            TravelRequest request, String sessionId, boolean live) {

        List<TransportOption> results = new ArrayList<>();
        List<CompletableFuture<List<TransportOption>>> futures = new ArrayList<>();

        if (request.transportTypes().contains("flight")) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                emit(live, sessionId, "Flight Agent", "searching", 20, "Searching flights...");
                List<TransportOption> r = flightAgent.searchFlights(request);
                emit(live, sessionId, "Flight Agent", "completed", 100, r.size() + " flights found");
                return r;
            }));
        }

        if (request.transportTypes().contains("car") || request.transportTypes().contains("bus")) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                emit(live, sessionId, "Car/Bus Agent", "searching", 20, "Searching ground transport...");
                List<TransportOption> r = carBusAgent.searchGroundTransport(request);
                emit(live, sessionId, "Car/Bus Agent", "completed", 100, r.size() + " ground options found");
                return r;
            }));
        }

        if (request.transportTypes().contains("train")) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                emit(live, sessionId, "Train Agent", "searching", 20, "Searching trains...");
                List<TransportOption> r = trainAgent.searchTrains(request);
                emit(live, sessionId, "Train Agent", "completed", 100, r.size() + " train routes found");
                return r;
            }));
        }

        if (request.transportTypes().contains("ferry")) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                emit(live, sessionId, "Ferry Agent", "searching", 20, "Searching ferries...");
                List<TransportOption> r = ferryAgent.searchFerries(request);
                emit(live, sessionId, "Ferry Agent", "completed", 100, r.size() + " ferry routes found");
                return r;
            }));
        }

        futures.forEach(f -> results.addAll(f.join()));
        return tierTransport(results, request);
    }

    // ── SSE helper ────────────────────────────────────────────────────────────

    private void emit(boolean live, String sessionId, String agent,
                      String status, int progress, String message) {
        if (live) progressService.update(sessionId, agent, status, progress, message);
    }

    // ── tiering ───────────────────────────────────────────────────────────────

    private List<AccommodationOption> tierAccommodations(
            List<AccommodationOption> list, TravelRequest request) {
        if (list.isEmpty()) return list;
        double mid = (request.budgetMax() - request.budgetMin()) / 2.0;
        double budgetLine  = mid * 0.7;
        double luxuryLine  = mid * 1.3;
        return list.stream().map(a -> {
            String tier = a.pricePerNight() <= budgetLine ? "budget"
                        : a.pricePerNight() <= luxuryLine ? "standard" : "luxury";
            return new AccommodationOption(a.id(), a.type(), a.name(), a.pricePerNight(),
                a.totalPrice(), a.rating(), a.location(), a.amenities(), a.bookingUrl(),
                tier, a.source(), a.imageUrl());
        }).collect(Collectors.toList());
    }

    private List<TransportOption> tierTransport(
            List<TransportOption> list, TravelRequest request) {
        if (list.isEmpty()) return list;
        double avg = list.stream().mapToDouble(TransportOption::price).average().orElse(200);
        double budgetLine = avg * 0.7;
        double luxuryLine = avg * 1.3;
        return list.stream().map(t -> {
            String tier = t.price() <= budgetLine ? "budget"
                        : t.price() <= luxuryLine ? "standard" : "luxury";
            return new TransportOption(t.id(), t.type(), t.provider(), t.departureTime(),
                t.arrivalTime(), t.duration(), t.price(), t.stops(), t.bookingUrl(),
                tier, t.source(), t.origin(), t.destination());
        }).collect(Collectors.toList());
    }

    // ── persistence helpers ───────────────────────────────────────────────────

    private void save(UnforgettableItinerary it) {
        try {
            ItineraryEntity entity = new ItineraryEntity(
                it.id(), it.destination(), it.startDate(), it.endDate(),
                it.guestCount(), it.totalEstimatedCost(), it.createdAt(),
                objectMapper.writeValueAsString(it.regionInsights()),
                objectMapper.writeValueAsString(it.accommodations()),
                objectMapper.writeValueAsString(it.transport())
            );
            entity.setAttractionsJson(objectMapper.writeValueAsString(it.attractions()));
            entity.setVariantsJson(objectMapper.writeValueAsString(it.variants()));
            entity.setWeatherJson(objectMapper.writeValueAsString(it.weatherForecast()));
            entity.setCurrencyJson(objectMapper.writeValueAsString(it.currencyInfo()));
            repository.save(entity);
        } catch (Exception e) {
            log.error("Failed to persist itinerary {}: {}", it.id(), e.getMessage());
        }
    }

    private UnforgettableItinerary toItinerary(ItineraryEntity e) {
        try {
            Map<String, Object> insights = parseJson(e.getRegionInsightsJson(), new TypeReference<>() {}, Map.of());
            List<AccommodationOption> accomms = parseJson(e.getAccommodationsJson(), new TypeReference<>() {}, List.of());
            List<TransportOption> transport = parseJson(e.getTransportJson(), new TypeReference<>() {}, List.of());
            List<AttractionOption> attractions = parseJson(e.getAttractionsJson(), new TypeReference<>() {}, List.of());
            List<ItineraryVariant> variants = parseJson(e.getVariantsJson(), new TypeReference<>() {}, List.of());
            Map<String, Object> weather = parseJson(e.getWeatherJson(), new TypeReference<>() {}, Map.of());
            Map<String, Object> currency = parseJson(e.getCurrencyJson(), new TypeReference<>() {}, Map.of());

            return new UnforgettableItinerary(e.getId(), e.getDestination(),
                e.getStartDate(), e.getEndDate(), e.getGuestCount(),
                insights, accomms, transport, attractions, variants,
                e.getTotalEstimatedCost(), weather, currency, e.getCreatedAt());
        } catch (Exception ex) {
            log.error("Failed to deserialise itinerary {}: {}", e.getId(), ex.getMessage());
            return new UnforgettableItinerary(e.getId(), e.getDestination(),
                e.getStartDate(), e.getEndDate(), e.getGuestCount(),
                Map.of(), List.of(), List.of(), e.getTotalEstimatedCost(), e.getCreatedAt());
        }
    }

    private <T> T parseJson(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) return fallback;
        try { return objectMapper.readValue(json, type); }
        catch (Exception e) { return fallback; }
    }
}
