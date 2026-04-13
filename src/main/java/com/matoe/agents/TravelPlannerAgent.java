package com.matoe.agents;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.matoe.domain.*;
import com.matoe.service.AgentProgressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Embabel GOAP Agent — the top-level agent registered with AgentPlatform.
 *
 * GOAP type-based planning:
 *   TravelRequest (blackboard input)
 *     → gatherIntelligence → TravelIntelligence
 *     → searchAccommodations → AccommodationResults
 *     → searchTransport → TransportResults
 *     → searchAttractions → AttractionResults
 *     → synthesize(TravelRequest, TravelIntelligence, AccommodationResults,
 *                   TransportResults, AttractionResults) → UnforgettableItinerary [GOAL]
 *
 * Actions with independent preconditions (all only need TravelRequest)
 * run in parallel when embabel.agent.platform.process-type=CONCURRENT.
 */
@Component
@Agent(
    name = "TravelPlanner",
    description = "Plan an unforgettable multi-day trip with accommodation, transport, and attractions"
)
public class TravelPlannerAgent {

    private static final Logger log = LoggerFactory.getLogger(TravelPlannerAgent.class);

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
    private final OrchestratorAgent orchestratorAgent;
    private final AgentProgressService progressService;
    private final ExecutorService agentExecutor;

    public TravelPlannerAgent(
            HotelAgent hotelAgent, BBAgent bbAgent,
            ApartmentAgent apartmentAgent, HostelAgent hostelAgent,
            FlightAgent flightAgent, CarBusAgent carBusAgent,
            TrainAgent trainAgent, FerryAgent ferryAgent,
            CountrySpecialistAgent countrySpecialistAgent,
            AttractionsAgent attractionsAgent,
            WeatherAgent weatherAgent, CurrencyAgent currencyAgent,
            ReviewSummaryAgent reviewSummaryAgent,
            OrchestratorAgent orchestratorAgent,
            AgentProgressService progressService,
            @Qualifier("agentExecutor") ExecutorService agentExecutor) {
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
        this.orchestratorAgent = orchestratorAgent;
        this.progressService = progressService;
        this.agentExecutor = agentExecutor;
    }

    // ── GOAP wrapper types (on the blackboard) ───────────────────────────────

    /** Wrapper: all intelligence data gathered for the destination. */
    public record TravelIntelligence(
        Map<String, Object> regionInsights,
        Map<String, Object> weatherForecast,
        Map<String, Object> currencyInfo,
        Map<String, Object> reviewSummary
    ) {}

    /** Wrapper: all accommodation results. */
    public record AccommodationResults(List<AccommodationOption> items) {}

    /** Wrapper: all transport results. */
    public record TransportResults(List<TransportOption> items) {}

    /** Wrapper: all attraction results. */
    public record AttractionResults(List<AttractionOption> items) {}

    // ── GOAP Actions ─────────────────────────────────────────────────────────

    /**
     * Gather all destination intelligence in parallel.
     * GOAP precondition: TravelRequest on blackboard.
     * GOAP effect: TravelIntelligence on blackboard.
     */
    @Action(description = "Gather regional insights, weather, currency, and reviews for the destination")
    public TravelIntelligence gatherIntelligence(TravelRequest request) {
        String sid = request.sessionId();

        CompletableFuture<Map<String, Object>> insightsFuture = CompletableFuture.supplyAsync(() -> {
            emit(sid, "Country Specialist", "searching", 15, "Researching destination...");
            Map<String, Object> r = countrySpecialistAgent.gatherRegionalInsights(
                request.destination(), request.orchestratorModel());
            emit(sid, "Country Specialist", "completed", 100, "Insights ready");
            return r;
        }, agentExecutor);
        CompletableFuture<Map<String, Object>> weatherFuture = CompletableFuture.supplyAsync(() -> {
            emit(sid, "Weather Agent", "searching", 15, "Checking weather...");
            Map<String, Object> r = weatherAgent.getWeatherForecast(request);
            emit(sid, "Weather Agent", "completed", 100, "Weather ready");
            return r;
        }, agentExecutor);
        CompletableFuture<Map<String, Object>> currencyFuture = CompletableFuture.supplyAsync(() -> {
            emit(sid, "Currency Agent", "searching", 15, "Checking currency...");
            Map<String, Object> r = currencyAgent.getCurrencyInfo(request);
            emit(sid, "Currency Agent", "completed", 100, "Currency ready");
            return r;
        }, agentExecutor);
        CompletableFuture<Map<String, Object>> reviewFuture = CompletableFuture.supplyAsync(() -> {
            emit(sid, "Review Agent", "searching", 15, "Aggregating reviews...");
            Map<String, Object> r = reviewSummaryAgent.getReviewSummary(request);
            emit(sid, "Review Agent", "completed", 100, "Reviews ready");
            return r;
        }, agentExecutor);

        CompletableFuture.allOf(insightsFuture, weatherFuture, currencyFuture, reviewFuture).join();

        return new TravelIntelligence(
            insightsFuture.join(), weatherFuture.join(),
            currencyFuture.join(), reviewFuture.join()
        );
    }

    /**
     * Search all requested accommodation types in parallel.
     * GOAP precondition: TravelRequest on blackboard.
     * GOAP effect: AccommodationResults on blackboard.
     */
    @Action(description = "Search accommodations across all requested types")
    public AccommodationResults searchAccommodations(TravelRequest request) {
        String sid = request.sessionId();
        List<CompletableFuture<List<AccommodationOption>>> futures = new ArrayList<>();

        if (request.accommodationTypes().contains("hotel")) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                emit(sid, "Hotel Agent", "searching", 20, "Searching hotels...");
                List<AccommodationOption> r = hotelAgent.searchHotels(request);
                emit(sid, "Hotel Agent", "completed", 100, r.size() + " hotels found");
                return r;
            }, agentExecutor));
        }
        if (request.accommodationTypes().contains("bb")) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                emit(sid, "B&B Agent", "searching", 20, "Searching B&Bs...");
                List<AccommodationOption> r = bbAgent.searchBBs(request);
                emit(sid, "B&B Agent", "completed", 100, r.size() + " B&Bs found");
                return r;
            }, agentExecutor));
        }
        if (request.accommodationTypes().contains("apartment")) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                emit(sid, "Apartment Agent", "searching", 20, "Searching apartments...");
                List<AccommodationOption> r = apartmentAgent.searchApartments(request);
                emit(sid, "Apartment Agent", "completed", 100, r.size() + " apartments found");
                return r;
            }, agentExecutor));
        }
        if (request.accommodationTypes().contains("hostel")) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                emit(sid, "Hostel Agent", "searching", 20, "Searching hostels...");
                List<AccommodationOption> r = hostelAgent.searchHostels(request);
                emit(sid, "Hostel Agent", "completed", 100, r.size() + " hostels found");
                return r;
            }, agentExecutor));
        }

        List<AccommodationOption> all = new ArrayList<>();
        futures.forEach(f -> all.addAll(f.join()));
        // FR-023 / FR-024: deduplicate by (name + location) keeping the cheapest price-per-night
        List<AccommodationOption> deduped = dedupeAccommodations(all);
        log.info("Accommodations: {} raw → {} after dedupe/best-price", all.size(), deduped.size());
        return new AccommodationResults(deduped);
    }

    /**
     * Deduplicate accommodations by normalised (name + location) key, retaining
     * the lowest {@code pricePerNight} when duplicates are found. Prefers
     * results with {@code source=browser} over {@code source=llm} when prices
     * tie. Exposed as package-private for testing.
     */
    static List<AccommodationOption> dedupeAccommodations(List<AccommodationOption> list) {
        if (list == null || list.isEmpty()) return List.of();
        Map<String, AccommodationOption> best = new LinkedHashMap<>();
        for (AccommodationOption a : list) {
            if (a == null || a.name() == null) continue;
            String key = (a.name().trim().toLowerCase(Locale.ROOT)
                + "|" + (a.location() == null ? "" : a.location().trim().toLowerCase(Locale.ROOT)));
            AccommodationOption prior = best.get(key);
            if (prior == null || isBetter(a, prior)) {
                best.put(key, a);
            }
        }
        return new ArrayList<>(best.values());
    }

    /** Better = strictly cheaper, OR same price but with real-source provenance. */
    private static boolean isBetter(AccommodationOption candidate, AccommodationOption incumbent) {
        if (candidate.pricePerNight() < incumbent.pricePerNight()) return true;
        if (candidate.pricePerNight() == incumbent.pricePerNight()) {
            return "browser".equalsIgnoreCase(candidate.source())
                && !"browser".equalsIgnoreCase(incumbent.source());
        }
        return false;
    }

    /**
     * Search all requested transport types in parallel.
     */
    @Action(description = "Search transport across all requested types")
    public TransportResults searchTransport(TravelRequest request) {
        String sid = request.sessionId();
        List<CompletableFuture<List<TransportOption>>> futures = new ArrayList<>();

        if (request.transportTypes().contains("flight")) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                emit(sid, "Flight Agent", "searching", 20, "Searching flights...");
                List<TransportOption> r = flightAgent.searchFlights(request);
                emit(sid, "Flight Agent", "completed", 100, r.size() + " flights found");
                return r;
            }, agentExecutor));
        }
        if (request.transportTypes().contains("car") || request.transportTypes().contains("bus")) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                emit(sid, "Car/Bus Agent", "searching", 20, "Searching ground transport...");
                List<TransportOption> r = carBusAgent.searchGroundTransport(request);
                emit(sid, "Car/Bus Agent", "completed", 100, r.size() + " options found");
                return r;
            }, agentExecutor));
        }
        if (request.transportTypes().contains("train")) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                emit(sid, "Train Agent", "searching", 20, "Searching trains...");
                List<TransportOption> r = trainAgent.searchTrains(request);
                emit(sid, "Train Agent", "completed", 100, r.size() + " train routes found");
                return r;
            }, agentExecutor));
        }
        if (request.transportTypes().contains("ferry")) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                emit(sid, "Ferry Agent", "searching", 20, "Searching ferries...");
                List<TransportOption> r = ferryAgent.searchFerries(request);
                emit(sid, "Ferry Agent", "completed", 100, r.size() + " ferry routes found");
                return r;
            }, agentExecutor));
        }

        List<TransportOption> all = new ArrayList<>();
        futures.forEach(f -> all.addAll(f.join()));
        List<TransportOption> deduped = dedupeTransport(all);
        log.info("Transport: {} raw → {} after dedupe/best-price", all.size(), deduped.size());
        return new TransportResults(deduped);
    }

    /**
     * Deduplicate transport by (type + provider + origin + destination), keeping
     * the cheapest {@code price}. Prefers {@code source=browser} on price ties.
     */
    static List<TransportOption> dedupeTransport(List<TransportOption> list) {
        if (list == null || list.isEmpty()) return List.of();
        Map<String, TransportOption> best = new LinkedHashMap<>();
        for (TransportOption t : list) {
            if (t == null) continue;
            String key = String.join("|",
                String.valueOf(t.type()).toLowerCase(Locale.ROOT),
                String.valueOf(t.provider()).toLowerCase(Locale.ROOT),
                String.valueOf(t.origin()).toLowerCase(Locale.ROOT),
                String.valueOf(t.destination()).toLowerCase(Locale.ROOT));
            TransportOption prior = best.get(key);
            if (prior == null || isBetterTransport(t, prior)) {
                best.put(key, t);
            }
        }
        return new ArrayList<>(best.values());
    }

    private static boolean isBetterTransport(TransportOption candidate, TransportOption incumbent) {
        if (candidate.price() < incumbent.price()) return true;
        if (candidate.price() == incumbent.price()) {
            return "browser".equalsIgnoreCase(candidate.source())
                && !"browser".equalsIgnoreCase(incumbent.source());
        }
        return false;
    }

    /**
     * Search attractions and experiences.
     */
    @Action(description = "Search attractions and experiences at the destination")
    public AttractionResults searchAttractions(TravelRequest request) {
        String sid = request.sessionId();
        emit(sid, "Attractions Agent", "searching", 20, "Finding attractions...");
        List<AttractionOption> results = attractionsAgent.searchAttractions(request);
        emit(sid, "Attractions Agent", "completed", 100, results.size() + " attractions found");
        return new AttractionResults(results);
    }

    /**
     * Terminal GOAP action — synthesize the final itinerary from all gathered data.
     * GOAP preconditions: TravelRequest, TravelIntelligence, AccommodationResults,
     *                     TransportResults, AttractionResults all on blackboard.
     * GOAP effect: UnforgettableItinerary (the goal type).
     */
    @AchievesGoal(description = "Plan an unforgettable trip with 3 tier variants and day-by-day breakdown")
    @Action(description = "Synthesize all search results into a final 3-tier itinerary")
    public UnforgettableItinerary synthesize(
            TravelRequest request,
            TravelIntelligence intelligence,
            AccommodationResults accommodations,
            TransportResults transport,
            AttractionResults attractions) {

        String sid = request.sessionId();
        emit(sid, "Orchestrator", "analyzing", 90, "Synthesising itinerary...");

        // Merge review data into insights
        Map<String, Object> insights = new HashMap<>(intelligence.regionInsights());
        if (intelligence.reviewSummary() != null && !intelligence.reviewSummary().isEmpty()) {
            insights.put("reviewSummary", intelligence.reviewSummary());
        }

        UnforgettableItinerary itinerary = orchestratorAgent.synthesizeItinerary(
            request,
            accommodations.items(),
            transport.items(),
            attractions.items(),
            insights,
            intelligence.weatherForecast(),
            intelligence.currencyInfo()
        );

        emit(sid, "Orchestrator", "completed", 100, "Itinerary ready!");
        return itinerary;
    }

    private void emit(String sessionId, String agent, String status, int progress, String message) {
        if (sessionId != null && !sessionId.isBlank()) {
            progressService.update(sessionId, agent, status, progress, message);
        }
    }
}
