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

    private final OrchestratorAgent   orchestratorAgent;
    private final HotelAgent          hotelAgent;
    private final BBAgent             bbAgent;
    private final ApartmentAgent      apartmentAgent;
    private final FlightAgent         flightAgent;
    private final CarBusAgent         carBusAgent;
    private final CountrySpecialistAgent countrySpecialistAgent;
    private final AgentProgressService progressService;
    private final ItineraryRepository  repository;
    private final ObjectMapper         objectMapper;

    public TravelService(
            OrchestratorAgent orchestratorAgent,
            HotelAgent hotelAgent, BBAgent bbAgent,
            ApartmentAgent apartmentAgent,
            FlightAgent flightAgent, CarBusAgent carBusAgent,
            CountrySpecialistAgent countrySpecialistAgent,
            AgentProgressService progressService,
            ItineraryRepository repository,
            ObjectMapper objectMapper) {
        this.orchestratorAgent      = orchestratorAgent;
        this.hotelAgent             = hotelAgent;
        this.bbAgent                = bbAgent;
        this.apartmentAgent         = apartmentAgent;
        this.flightAgent            = flightAgent;
        this.carBusAgent            = carBusAgent;
        this.countrySpecialistAgent = countrySpecialistAgent;
        this.progressService        = progressService;
        this.repository             = repository;
        this.objectMapper           = objectMapper;
    }

    // ── public API ────────────────────────────────────────────────────────────

    public UnforgettableItinerary planTrip(TravelRequest request, String sessionId) {
        boolean live = sessionId != null && !sessionId.isBlank();

        emit(live, sessionId, "Orchestrator", "deployed", 5, "Initialising agent swarm…");

        // ── parallel: country insights + accommodations + transport ────────────
        CompletableFuture<Map<String, Object>> insightsFuture = CompletableFuture.supplyAsync(() -> {
            emit(live, sessionId, "Country Specialist", "searching", 15, "Researching destination…");
            Map<String, Object> result = countrySpecialistAgent.gatherRegionalInsights(
                request.destination(), request.orchestratorModel());
            emit(live, sessionId, "Country Specialist", "completed", 100, "Insights ready");
            return result;
        });

        CompletableFuture<List<AccommodationOption>> accommodationFuture = CompletableFuture.supplyAsync(() ->
            searchAccommodations(request, sessionId, live));

        CompletableFuture<List<TransportOption>> transportFuture = CompletableFuture.supplyAsync(() ->
            searchTransport(request, sessionId, live));

        CompletableFuture.allOf(insightsFuture, accommodationFuture, transportFuture).join();

        List<AccommodationOption> accommodations = accommodationFuture.join();
        List<TransportOption>     transport      = transportFuture.join();
        Map<String, Object>       insights       = insightsFuture.join();

        // ── synthesise ────────────────────────────────────────────────────────
        emit(live, sessionId, "Orchestrator", "analyzing", 90, "Synthesising itinerary…");
        UnforgettableItinerary itinerary = orchestratorAgent.synthesizeItinerary(
            request, accommodations, transport, insights);

        // ── persist ───────────────────────────────────────────────────────────
        save(itinerary);

        emit(live, sessionId, "Orchestrator", "completed", 100, "Itinerary ready!");
        progressService.complete(sessionId);

        return itinerary;
    }

    public List<UnforgettableItinerary> getAllItineraries() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
            .map(this::toItinerary)
            .collect(Collectors.toList());
    }

    public List<UnforgettableItinerary> searchItineraries(String destination) {
        return repository.findByDestinationContainingIgnoreCaseOrderByCreatedAtDesc(destination)
            .stream().map(this::toItinerary).collect(Collectors.toList());
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private List<AccommodationOption> searchAccommodations(
            TravelRequest request, String sessionId, boolean live) {

        List<AccommodationOption> results = new ArrayList<>();
        List<CompletableFuture<List<AccommodationOption>>> futures = new ArrayList<>();

        if (request.accommodationTypes().contains("hotel")) {
            emit(live, sessionId, "Hotel Agent", "searching", 20, "Searching hotels…");
            futures.add(CompletableFuture.supplyAsync(() -> {
                List<AccommodationOption> r = hotelAgent.searchHotels(request);
                emit(live, sessionId, "Hotel Agent", "completed", 100, r.size() + " hotels found");
                return r;
            }));
        }

        if (request.accommodationTypes().contains("bb")) {
            emit(live, sessionId, "B&B Agent", "searching", 20, "Searching B&Bs…");
            futures.add(CompletableFuture.supplyAsync(() -> {
                List<AccommodationOption> r = bbAgent.searchBB(request);
                emit(live, sessionId, "B&B Agent", "completed", 100, r.size() + " B&Bs found");
                return r;
            }));
        }

        if (request.accommodationTypes().contains("apartment")) {
            emit(live, sessionId, "Apartment Agent", "searching", 20, "Searching apartments…");
            futures.add(CompletableFuture.supplyAsync(() -> {
                List<AccommodationOption> r = apartmentAgent.searchApartments(request);
                emit(live, sessionId, "Apartment Agent", "completed", 100, r.size() + " apartments found");
                return r;
            }));
        }

        futures.forEach(f -> results.addAll(f.join()));
        return tierAccommodations(results, request);
    }

    private List<TransportOption> searchTransport(
            TravelRequest request, String sessionId, boolean live) {

        List<TransportOption> results = new ArrayList<>();
        List<CompletableFuture<List<TransportOption>>> futures = new ArrayList<>();

        if (request.transportTypes().contains("flight")) {
            emit(live, sessionId, "Flight Agent", "searching", 20, "Searching flights…");
            futures.add(CompletableFuture.supplyAsync(() -> {
                List<TransportOption> r = flightAgent.searchFlights(request);
                emit(live, sessionId, "Flight Agent", "completed", 100, r.size() + " flights found");
                return r;
            }));
        }

        if (request.transportTypes().contains("car") || request.transportTypes().contains("bus")) {
            emit(live, sessionId, "Car/Bus Agent", "searching", 20, "Searching ground transport…");
            futures.add(CompletableFuture.supplyAsync(() -> {
                List<TransportOption> r = carBusAgent.searchGroundTransport(request);
                emit(live, sessionId, "Car/Bus Agent", "completed", 100, r.size() + " ground options found");
                return r;
            }));
        }

        futures.forEach(f -> results.addAll(f.join()));
        return tierTransport(results, request);
    }

    private void emit(boolean live, String sessionId, String agent,
                      String status, int progress, String message) {
        if (live) progressService.update(sessionId, agent, status, progress, message);
    }

    // ── tiering ───────────────────────────────────────────────────────────────

    private List<AccommodationOption> tierAccommodations(
            List<AccommodationOption> list, TravelRequest request) {
        double mid = (request.budgetMax() - request.budgetMin()) / 2.0;
        double budgetLine  = mid * 0.7;
        double luxuryLine  = mid * 1.3;
        return list.stream().map(a -> {
            String tier = a.pricePerNight() <= budgetLine ? "budget"
                        : a.pricePerNight() <= luxuryLine ? "standard" : "luxury";
            return new AccommodationOption(a.id(), a.type(), a.name(), a.pricePerNight(),
                a.totalPrice(), a.rating(), a.location(), a.amenities(), a.bookingUrl(), tier);
        }).collect(Collectors.toList());
    }

    private List<TransportOption> tierTransport(
            List<TransportOption> list, TravelRequest request) {
        double avg = list.stream().mapToDouble(TransportOption::price).average().orElse(200);
        double budgetLine = avg * 0.7;
        double luxuryLine = avg * 1.3;
        return list.stream().map(t -> {
            String tier = t.price() <= budgetLine ? "budget"
                        : t.price() <= luxuryLine ? "standard" : "luxury";
            return new TransportOption(t.id(), t.type(), t.provider(), t.departureTime(),
                t.arrivalTime(), t.duration(), t.price(), t.stops(), t.bookingUrl(), tier);
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
            repository.save(entity);
        } catch (Exception e) {
            log.error("Failed to persist itinerary {}: {}", it.id(), e.getMessage());
        }
    }

    private UnforgettableItinerary toItinerary(ItineraryEntity e) {
        try {
            Map<String, Object> insights = e.getRegionInsightsJson() != null
                ? objectMapper.readValue(e.getRegionInsightsJson(), new TypeReference<>() {}) : Map.of();
            List<AccommodationOption> accomms = e.getAccommodationsJson() != null
                ? objectMapper.readValue(e.getAccommodationsJson(), new TypeReference<>() {}) : List.of();
            List<TransportOption> transport = e.getTransportJson() != null
                ? objectMapper.readValue(e.getTransportJson(), new TypeReference<>() {}) : List.of();

            return new UnforgettableItinerary(e.getId(), e.getDestination(),
                e.getStartDate(), e.getEndDate(), e.getGuestCount(),
                insights, accomms, transport,
                e.getTotalEstimatedCost(), e.getCreatedAt());
        } catch (Exception ex) {
            log.error("Failed to deserialise itinerary {}: {}", e.getId(), ex.getMessage());
            return new UnforgettableItinerary(e.getId(), e.getDestination(),
                e.getStartDate(), e.getEndDate(), e.getGuestCount(),
                Map.of(), List.of(), List.of(), e.getTotalEstimatedCost(), e.getCreatedAt());
        }
    }
}
