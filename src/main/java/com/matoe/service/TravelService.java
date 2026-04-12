package com.matoe.service;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.ProcessOptions;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matoe.agents.*;
import com.matoe.agents.TravelPlannerAgent.*;
import com.matoe.domain.*;
import com.matoe.entity.ItineraryEntity;
import com.matoe.repository.ItineraryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core orchestration service.
 *
 * Primary path: uses Embabel AgentPlatform to execute the TravelPlannerAgent
 * via GOAP planning. The planner chains actions by their type signatures
 * and runs independent actions in parallel (CONCURRENT mode).
 *
 * Fallback path: if AgentPlatform is not available (e.g., Embabel starters
 * not resolved), falls back to direct CompletableFuture dispatch.
 */
@Service
public class TravelService {

    private static final Logger log = LoggerFactory.getLogger(TravelService.class);

    @Autowired(required = false)
    private AgentPlatform agentPlatform;

    private final TravelPlannerAgent travelPlannerAgent;
    private final AgentProgressService progressService;
    private final LlmCostTrackingService costTracker;
    private final ItineraryRepository repository;
    private final ObjectMapper objectMapper;

    public TravelService(
            TravelPlannerAgent travelPlannerAgent,
            AgentProgressService progressService,
            LlmCostTrackingService costTracker,
            ItineraryRepository repository,
            ObjectMapper objectMapper) {
        this.travelPlannerAgent = travelPlannerAgent;
        this.progressService = progressService;
        this.costTracker = costTracker;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    // ── public API ────────────────────────────────────────────────────────────

    public UnforgettableItinerary planTrip(TravelRequest request, String sessionId) {
        boolean live = sessionId != null && !sessionId.isBlank();
        emit(live, sessionId, "Orchestrator", "deployed", 5, "Initialising agent swarm...");

        // Check budget ceiling before starting
        if (sessionId != null && costTracker.isBudgetExceeded(sessionId)) {
            log.warn("Session {} budget already exceeded — aborting", sessionId);
            throw new RuntimeException("LLM budget ceiling exceeded for this session");
        }

        UnforgettableItinerary itinerary;

        if (agentPlatform != null) {
            // ── PRIMARY: Embabel GOAP execution ──────────────────────────────
            log.info("Using Embabel AgentPlatform for GOAP-planned execution");
            itinerary = executeViaEmbabel(request, sessionId);
        } else {
            // ── FALLBACK: direct agent dispatch ──────────────────────────────
            log.info("AgentPlatform not available, using direct dispatch");
            itinerary = executeDirectly(request, sessionId);
        }

        // Tier the raw results
        itinerary = applyTiering(itinerary, request);

        // Persist
        save(itinerary);

        emit(live, sessionId, "Orchestrator", "completed", 100, "Itinerary ready!");
        progressService.complete(sessionId);

        return itinerary;
    }

    // ── Embabel GOAP path ─────────────────────────────────────────────────────

    private UnforgettableItinerary executeViaEmbabel(TravelRequest request, String sessionId) {
        try {
            // Find the TravelPlanner agent in the platform
            var agent = agentPlatform.agents().stream()
                .filter(a -> "TravelPlanner".equals(a.getName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("TravelPlanner agent not found in platform"));

            // Run with the TravelRequest on the blackboard
            ProcessOptions options = new ProcessOptions();
            AgentProcess process = agentPlatform.runAgentFrom(
                agent, options, Map.of("request", request));

            // The GOAP planner will:
            // 1. See TravelRequest on the blackboard
            // 2. Plan: gatherIntelligence, searchAccommodations, searchTransport,
            //          searchAttractions can all run in parallel (all need only TravelRequest)
            // 3. Then: synthesize needs all 4 outputs + TravelRequest → runs last
            // 4. Result: UnforgettableItinerary on the blackboard

            AgentProcess completed = process.run();
            return completed.resultOfType(UnforgettableItinerary.class);

        } catch (Exception e) {
            log.warn("Embabel GOAP execution failed, falling back to direct: {}", e.getMessage());
            return executeDirectly(request, sessionId);
        }
    }

    // ── Direct dispatch path (fallback) ───────────────────────────────────────

    private UnforgettableItinerary executeDirectly(TravelRequest request, String sessionId) {
        // Phase 1: intelligence + accommodation + transport + attractions in parallel
        var intelligenceFuture = java.util.concurrent.CompletableFuture.supplyAsync(() ->
            travelPlannerAgent.gatherIntelligence(request));
        var accommodationFuture = java.util.concurrent.CompletableFuture.supplyAsync(() ->
            travelPlannerAgent.searchAccommodations(request));
        var transportFuture = java.util.concurrent.CompletableFuture.supplyAsync(() ->
            travelPlannerAgent.searchTransport(request));
        var attractionsFuture = java.util.concurrent.CompletableFuture.supplyAsync(() ->
            travelPlannerAgent.searchAttractions(request));

        java.util.concurrent.CompletableFuture.allOf(
            intelligenceFuture, accommodationFuture, transportFuture, attractionsFuture
        ).join();

        // Check budget mid-flight
        if (sessionId != null && costTracker.isBudgetExceeded(sessionId)) {
            log.warn("Session {} budget exceeded mid-execution — skipping synthesis", sessionId);
            // Return raw results without LLM synthesis
            TravelIntelligence intel = intelligenceFuture.join();
            return new UnforgettableItinerary(
                UUID.randomUUID().toString(), request.destination(),
                request.startDate().toString(), request.endDate().toString(),
                request.guestCount(), intel.regionInsights(),
                accommodationFuture.join().items(), transportFuture.join().items(),
                attractionsFuture.join().items(), List.of(),
                0.0, intel.weatherForecast(), intel.currencyInfo(),
                java.time.LocalDateTime.now()
            );
        }

        // Phase 2: synthesize
        return travelPlannerAgent.synthesize(
            request,
            intelligenceFuture.join(),
            accommodationFuture.join(),
            transportFuture.join(),
            attractionsFuture.join()
        );
    }

    // ── Tiering ───────────────────────────────────────────────────────────────

    private UnforgettableItinerary applyTiering(UnforgettableItinerary it, TravelRequest request) {
        List<AccommodationOption> tiered = tierAccommodations(it.accommodations(), request);
        List<TransportOption> tieredTransport = tierTransport(it.transport(), request);
        return new UnforgettableItinerary(
            it.id(), it.destination(), it.startDate(), it.endDate(), it.guestCount(),
            it.regionInsights(), tiered, tieredTransport, it.attractions(), it.variants(),
            it.totalEstimatedCost(), it.weatherForecast(), it.currencyInfo(), it.createdAt()
        );
    }

    private List<AccommodationOption> tierAccommodations(
            List<AccommodationOption> list, TravelRequest request) {
        if (list == null || list.isEmpty()) return List.of();
        double mid = (request.budgetMax() - request.budgetMin()) / 2.0;
        double budgetLine = mid * 0.7;
        double luxuryLine = mid * 1.3;
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
        if (list == null || list.isEmpty()) return List.of();
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

    // ── Query API ─────────────────────────────────────────────────────────────

    public List<UnforgettableItinerary> getAllItineraries() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
            .map(this::toItinerary).collect(Collectors.toList());
    }

    public List<UnforgettableItinerary> searchItineraries(String destination) {
        return repository.findByDestinationContainingIgnoreCaseOrderByCreatedAtDesc(destination)
            .stream().map(this::toItinerary).collect(Collectors.toList());
    }

    // ── SSE helper ────────────────────────────────────────────────────────────

    private void emit(boolean live, String sessionId, String agent,
                      String status, int progress, String message) {
        if (live) progressService.update(sessionId, agent, status, progress, message);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

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
