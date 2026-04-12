package com.matoe.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matoe.agents.*;
import com.matoe.agents.TravelPlannerAgent.*;
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
 *
 * Dispatches all specialist agents via TravelPlannerAgent (which carries
 * real Embabel @Agent/@Action/@AchievesGoal annotations for GOAP planning).
 * When AgentPlatform is available at runtime, Embabel's GOAP planner chains
 * the actions automatically. Otherwise, this service drives them directly
 * via CompletableFuture parallelism on Virtual Threads.
 *
 * Session ID handling: the controller passes sessionId as a query param.
 * This service creates a sessionId-enriched copy of TravelRequest so that
 * all downstream agents, cost tracking, and SSE progress use the same ID.
 */
@Service
public class TravelService {

    private static final Logger log = LoggerFactory.getLogger(TravelService.class);

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
        // Enrich request with sessionId so ALL downstream agents, cost tracking,
        // and SSE progress emit to the correct session
        TravelRequest enriched = withSessionId(request, sessionId);
        boolean live = sessionId != null && !sessionId.isBlank();

        emit(live, sessionId, "Orchestrator", "deployed", 5, "Initialising agent swarm...");

        // Check budget ceiling before starting
        if (sessionId != null && costTracker.isBudgetExceeded(sessionId)) {
            log.warn("Session {} budget already exceeded — aborting", sessionId);
            throw new RuntimeException("LLM budget ceiling exceeded for this session");
        }

        // Execute via TravelPlannerAgent (GOAP-annotated actions, parallel dispatch)
        UnforgettableItinerary itinerary = executeAgentPlan(enriched, sessionId);

        // Apply tiering to raw results
        itinerary = applyTiering(itinerary, enriched);

        // Persist
        save(itinerary);

        emit(live, sessionId, "Orchestrator", "completed", 100, "Itinerary ready!");
        progressService.complete(sessionId);

        return itinerary;
    }

    // ── Agent execution ───────────────────────────────────────────────────────

    /**
     * Execute the TravelPlannerAgent's GOAP actions.
     * The agent class carries real Embabel annotations — when AgentPlatform is
     * present at runtime, Embabel's GOAP planner drives execution. Here we call
     * the action methods directly with CompletableFuture parallelism, which
     * mirrors the GOAP plan (all search actions take TravelRequest → parallel,
     * synthesize takes all results → sequential last).
     */
    private UnforgettableItinerary executeAgentPlan(TravelRequest request, String sessionId) {
        // Phase 1: all search/intelligence actions in parallel
        // (These correspond to GOAP actions that all have TravelRequest as sole input type)
        CompletableFuture<TravelIntelligence> intelligenceFuture =
            CompletableFuture.supplyAsync(() -> travelPlannerAgent.gatherIntelligence(request));
        CompletableFuture<AccommodationResults> accommodationFuture =
            CompletableFuture.supplyAsync(() -> travelPlannerAgent.searchAccommodations(request));
        CompletableFuture<TransportResults> transportFuture =
            CompletableFuture.supplyAsync(() -> travelPlannerAgent.searchTransport(request));
        CompletableFuture<AttractionResults> attractionsFuture =
            CompletableFuture.supplyAsync(() -> travelPlannerAgent.searchAttractions(request));

        CompletableFuture.allOf(
            intelligenceFuture, accommodationFuture, transportFuture, attractionsFuture
        ).join();

        // Check budget mid-flight before expensive synthesis
        if (sessionId != null && costTracker.isBudgetExceeded(sessionId)) {
            log.warn("Session {} budget exceeded mid-execution — returning raw results", sessionId);
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

        // Phase 2: synthesize (GOAP terminal action — needs all Phase 1 outputs)
        return travelPlannerAgent.synthesize(
            request,
            intelligenceFuture.join(),
            accommodationFuture.join(),
            transportFuture.join(),
            attractionsFuture.join()
        );
    }

    // ── Session ID propagation ────────────────────────────────────────────────

    /**
     * Create a copy of TravelRequest with the sessionId injected.
     * The controller receives sessionId as a query param, separate from the
     * JSON body. This ensures all agents, cost tracking, and SSE use it.
     */
    private TravelRequest withSessionId(TravelRequest r, String sessionId) {
        return new TravelRequest(
            r.destination(), r.destinations(), r.startDate(), r.endDate(),
            r.guestCount(), r.budgetMin(), r.budgetMax(), r.travelStyle(),
            r.accommodationTypes(), r.transportTypes(), r.interestTags(),
            r.orchestratorModel(), r.extractorModel(), r.originCity(),
            sessionId
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
