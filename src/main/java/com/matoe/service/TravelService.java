package com.matoe.service;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matoe.agents.TravelPlannerAgent;
import com.matoe.agents.TravelPlannerAgent.*;
import com.matoe.domain.*;
import com.matoe.entity.ItineraryEntity;
import com.matoe.repository.ItineraryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Core orchestration service.
 *
 * <p><b>Execution strategy</b> (in order of preference):
 * <ol>
 *   <li><b>Embabel AgentPlatform</b> — if an {@link AgentPlatform} bean is
 *       available at runtime, execute the trip through Embabel's GOAP planner.
 *       {@link TravelPlannerAgent} carries {@code @Agent} / {@code @Action} /
 *       {@code @AchievesGoal} annotations; the platform chains them by type
 *       and runs independent actions concurrently when
 *       {@code embabel.agent.platform.process-type=CONCURRENT}.</li>
 *   <li><b>Virtual-thread fan-out</b> — if Embabel is not on the classpath or
 *       no platform bean is provided (e.g. tests, stripped deployments), fall
 *       back to a deterministic CompletableFuture dispatch on the
 *       {@code agentExecutor} virtual-thread pool. This mirrors the GOAP plan
 *       exactly: four parallel search/intel actions, then a synthesis step.</li>
 * </ol>
 *
 * <p><b>Session ID propagation</b>: the controller receives sessionId as a
 * query param (separate from the JSON body). {@link #withSessionId} creates a
 * copy of TravelRequest with the sessionId injected so ALL downstream agents,
 * cost tracking, and SSE progress emit to the correct session.
 */
@Service
public class TravelService {

    private static final Logger log = LoggerFactory.getLogger(TravelService.class);

    private final TravelPlannerAgent travelPlannerAgent;
    private final AgentProgressService progressService;
    private final LlmCostTrackingService costTracker;
    private final ItineraryRepository repository;
    private final ObjectMapper objectMapper;
    private final ExecutorService agentExecutor;
    private final AgentPlatform agentPlatform; // may be null

    public TravelService(
            TravelPlannerAgent travelPlannerAgent,
            AgentProgressService progressService,
            LlmCostTrackingService costTracker,
            ItineraryRepository repository,
            ObjectMapper objectMapper,
            @Qualifier("agentExecutor") ExecutorService agentExecutor,
            @Autowired(required = false) AgentPlatform agentPlatform) {
        this.travelPlannerAgent = travelPlannerAgent;
        this.progressService = progressService;
        this.costTracker = costTracker;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.agentExecutor = agentExecutor;
        this.agentPlatform = agentPlatform;
        log.info("TravelService initialised — AgentPlatform={}, executor=virtual-threads",
            agentPlatform != null ? "PRESENT (GOAP active)" : "absent (fallback mode)");
    }

    // ── public API ────────────────────────────────────────────────────────────

    public UnforgettableItinerary planTrip(TravelRequest request, String sessionId) {
        TravelRequest enriched = withSessionId(request, sessionId);
        boolean live = sessionId != null && !sessionId.isBlank();

        emit(live, sessionId, "Orchestrator", "deployed", 5, "Initialising agent swarm...");

        // Pre-flight budget check
        if (sessionId != null && costTracker.isBudgetExceeded(sessionId)) {
            log.warn("Session {} budget already exceeded — aborting", sessionId);
            throw new RuntimeException("LLM budget ceiling exceeded for this session");
        }

        UnforgettableItinerary itinerary;
        if (agentPlatform != null) {
            itinerary = runViaEmbabelGoap(enriched, sessionId);
        } else {
            itinerary = runViaVirtualThreads(enriched, sessionId);
        }

        // Apply tiering to raw results
        itinerary = applyTiering(itinerary, enriched);

        // Persist
        save(itinerary);

        emit(live, sessionId, "Orchestrator", "completed", 100, "Itinerary ready!");
        progressService.complete(sessionId);

        return itinerary;
    }

    // ── Execution Path 1: Embabel AgentPlatform (preferred) ───────────────────

    /**
     * Drive the trip through Embabel's GOAP planner. The planner reads the
     * {@code @Agent}/{@code @Action}/{@code @AchievesGoal} annotations on
     * {@link TravelPlannerAgent}, computes a plan (TravelRequest → parallel
     * search/intel actions → synthesis → UnforgettableItinerary), and runs it.
     */
    private UnforgettableItinerary runViaEmbabelGoap(TravelRequest request, String sessionId) {
        try {
            log.info("Running trip via Embabel AgentPlatform (GOAP)");
            // Seed the blackboard with the TravelRequest and kick off the agent
            AgentProcess process = agentPlatform.runAgentFrom(
                travelPlannerAgent,
                Map.of("travelRequest", request)
            );
            Object result = process.resultOfType(UnforgettableItinerary.class);
            if (result instanceof UnforgettableItinerary it) {
                return it;
            }
            log.warn("Embabel process returned unexpected type {} — falling back to virtual-thread path",
                result == null ? "null" : result.getClass().getName());
        } catch (Exception e) {
            log.warn("Embabel execution failed — falling back to virtual-thread path: {}", e.getMessage());
        }
        return runViaVirtualThreads(request, sessionId);
    }

    // ── Execution Path 2: Virtual-thread fan-out (fallback) ───────────────────

    /**
     * Invoke the planner's GOAP action methods directly on the virtual-thread
     * executor. Mirrors the GOAP plan: the four Phase 1 actions all take
     * {@code TravelRequest} as sole input so the planner runs them in parallel;
     * the synthesis action requires all Phase 1 outputs so it runs last.
     */
    private UnforgettableItinerary runViaVirtualThreads(TravelRequest request, String sessionId) {
        log.info("Running trip via virtual-thread fan-out (no AgentPlatform bean)");

        CompletableFuture<TravelIntelligence> intelligenceFuture =
            CompletableFuture.supplyAsync(() -> travelPlannerAgent.gatherIntelligence(request), agentExecutor);
        CompletableFuture<AccommodationResults> accommodationFuture =
            CompletableFuture.supplyAsync(() -> travelPlannerAgent.searchAccommodations(request), agentExecutor);
        CompletableFuture<TransportResults> transportFuture =
            CompletableFuture.supplyAsync(() -> travelPlannerAgent.searchTransport(request), agentExecutor);
        CompletableFuture<AttractionResults> attractionsFuture =
            CompletableFuture.supplyAsync(() -> travelPlannerAgent.searchAttractions(request), agentExecutor);

        CompletableFuture.allOf(
            intelligenceFuture, accommodationFuture, transportFuture, attractionsFuture
        ).join();

        // Mid-flight budget check before expensive synthesis
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

        return travelPlannerAgent.synthesize(
            request,
            intelligenceFuture.join(),
            accommodationFuture.join(),
            transportFuture.join(),
            attractionsFuture.join()
        );
    }

    // ── Session ID propagation ────────────────────────────────────────────────

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

    public UnforgettableItinerary getItinerary(String id) {
        return repository.findById(id).map(this::toItinerary).orElse(null);
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
