package com.matoe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matoe.agents.TravelPlannerAgent;
import com.matoe.agents.TravelPlannerAgent.*;
import com.matoe.domain.*;
import com.matoe.repository.ItineraryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests TravelService's Embabel dispatch strategy:
 *
 * <ol>
 *   <li><b>Lazy resolution</b>: AgentPlatform is NOT resolved at construction
 *       — only on the first trip request, so the app starts even if no LLM
 *       provider is running.</li>
 *   <li><b>Graceful fallback</b>: When AgentPlatform is absent or fails to
 *       resolve, TravelService falls back to virtual-thread fan-out through
 *       TravelPlannerAgent's action methods.</li>
 *   <li><b>Resolution failure handling</b>: If the ApplicationContext throws
 *       while resolving Embabel beans (e.g. model provider can't connect),
 *       TravelService catches and falls back — it does NOT crash.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class TravelServiceDispatchTest {

    @Mock private TravelPlannerAgent travelPlannerAgent;
    @Mock private AgentProgressService progressService;
    @Mock private LlmCostTrackingService costTracker;
    @Mock private ItineraryRepository repository;
    @Mock private ApplicationContext applicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private TravelRequest testRequest;
    private UnforgettableItinerary stubItinerary;

    @BeforeEach
    void setUp() {
        testRequest = new TravelRequest(
            "Paris", List.of("Paris"),
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5),
            2, 500.0, 2000.0, "standard",
            List.of("hotel"), List.of("flight"), List.of("food", "history"),
            "lmstudio/llama-3-8b", "lmstudio/llama-3-8b",
            "Amsterdam", "test-session"
        );

        stubItinerary = new UnforgettableItinerary(
            UUID.randomUUID().toString(), "Paris",
            "2026-06-01", "2026-06-05", 2,
            Map.of(), List.of(), List.of(), List.of(), List.of(),
            1000.0, Map.of(), Map.of(), LocalDateTime.now()
        );
    }

    @Test
    void constructorDoesNotResolveEmbabel() {
        // The ApplicationContext should NOT be queried for AgentPlatform during construction.
        // This verifies lazy resolution — critical for NAS startup without a running LLM.
        TravelService service = new TravelService(
            travelPlannerAgent, progressService, costTracker,
            repository, objectMapper, executor, applicationContext
        );

        // getBeansOfType should NOT have been called yet
        verify(applicationContext, never()).getBeansOfType(any());
        assertNotNull(service);
    }

    @Test
    void fallsBackToVirtualThreadsWhenNoEmbabel() {
        // ApplicationContext has no AgentPlatform beans
        when(applicationContext.getBeansOfType(any())).thenReturn(Map.of());
        stubVirtualThreadPath();

        TravelService service = new TravelService(
            travelPlannerAgent, progressService, costTracker,
            repository, objectMapper, executor, applicationContext
        );

        UnforgettableItinerary result = service.planTrip(testRequest, "session-1");

        assertNotNull(result);
        assertEquals("Paris", result.destination());
        // Virtual-thread path calls TravelPlannerAgent methods directly
        verify(travelPlannerAgent).gatherIntelligence(any());
        verify(travelPlannerAgent).searchAccommodations(any());
        verify(travelPlannerAgent).searchTransport(any());
        verify(travelPlannerAgent).searchAttractions(any());
        verify(travelPlannerAgent).synthesize(any(), any(), any(), any(), any());
    }

    @Test
    void fallsBackGracefullyWhenEmbabelResolutionThrows() {
        // Simulate Embabel model provider failing during bean creation
        when(applicationContext.getBeansOfType(any()))
            .thenThrow(new RuntimeException("Default LLM 'llama-3-8b' not found in available models: []"));
        stubVirtualThreadPath();

        TravelService service = new TravelService(
            travelPlannerAgent, progressService, costTracker,
            repository, objectMapper, executor, applicationContext
        );

        // Should NOT throw — catches Embabel failure and uses fallback
        UnforgettableItinerary result = service.planTrip(testRequest, "session-2");

        assertNotNull(result);
        verify(travelPlannerAgent).synthesize(any(), any(), any(), any(), any());
    }

    @Test
    void lazyResolutionOnlyHappensOnce() {
        when(applicationContext.getBeansOfType(any())).thenReturn(Map.of());
        stubVirtualThreadPath();

        TravelService service = new TravelService(
            travelPlannerAgent, progressService, costTracker,
            repository, objectMapper, executor, applicationContext
        );

        // Two trips — Embabel resolution should only happen once
        service.planTrip(testRequest, "session-a");
        service.planTrip(testRequest, "session-b");

        // getBeansOfType called exactly once (lazy, then cached)
        verify(applicationContext, times(1)).getBeansOfType(any());
    }

    /**
     * Stub the TravelPlannerAgent methods for the virtual-thread fallback path.
     */
    private void stubVirtualThreadPath() {
        when(costTracker.isBudgetExceeded(any())).thenReturn(false);

        TravelIntelligence intel = new TravelIntelligence(
            Map.of("language", "French"), Map.of("temp", "20C"),
            Map.of("currency", "EUR"), Map.of()
        );
        when(travelPlannerAgent.gatherIntelligence(any())).thenReturn(intel);
        when(travelPlannerAgent.searchAccommodations(any()))
            .thenReturn(new AccommodationResults(List.of()));
        when(travelPlannerAgent.searchTransport(any()))
            .thenReturn(new TransportResults(List.of()));
        when(travelPlannerAgent.searchAttractions(any()))
            .thenReturn(new AttractionResults(List.of()));
        when(travelPlannerAgent.synthesize(any(), any(), any(), any(), any()))
            .thenReturn(stubItinerary);
    }
}
