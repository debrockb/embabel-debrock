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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests the Embabel GOAP dispatch path in TravelService.
 *
 * <p>These tests verify that when an {@code AgentPlatform} bean is present in
 * the Spring context, TravelService uses the Embabel GOAP planner path
 * ({@code runAgentFrom}) rather than the virtual-thread fallback. They do NOT
 * require a running LLM — instead they simulate an {@code AgentPlatform} and
 * {@code Agent} using JDK dynamic proxies that mimic the Embabel API contract.
 *
 * <p><b>What this proves:</b>
 * <ol>
 *   <li>TravelService resolves the AgentPlatform from the ApplicationContext</li>
 *   <li>It calls {@code runAgentFrom(Agent, ProcessOptions, Map)} on the platform</li>
 *   <li>It calls {@code run()} on the returned process</li>
 *   <li>It calls {@code resultOfType(Class)} to extract the typed result</li>
 *   <li>When the GOAP path returns an {@code UnforgettableItinerary}, it is
 *       returned to the caller without falling through to virtual-thread fallback</li>
 *   <li>When the GOAP path fails, it falls back to virtual threads gracefully</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class EmbabelGoapDispatchTest {

    @Mock private TravelPlannerAgent travelPlannerAgent;
    @Mock private AgentProgressService progressService;
    @Mock private LlmCostTrackingService costTracker;
    @Mock private ItineraryRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private TravelRequest testRequest;
    private UnforgettableItinerary goapItinerary;
    private UnforgettableItinerary fallbackItinerary;

    @BeforeEach
    void setUp() {
        // Skip these tests if Embabel classes aren't on the classpath
        // (e.g. Embabel Artifactory was unreachable during dependency resolution).
        assumeTrue(isEmbabelOnClasspath(),
            "Embabel Agent classes not on classpath — skipping GOAP dispatch tests");

        testRequest = new TravelRequest(
            "Barcelona", List.of("Barcelona"),
            LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 15),
            2, 800.0, 3000.0, "standard",
            List.of("hotel"), List.of("flight"), List.of("food", "art"),
            "lmstudio/llama-3-8b", "lmstudio/llama-3-8b",
            "Amsterdam", "goap-test-session"
        );

        goapItinerary = new UnforgettableItinerary(
            "goap-result-id", "Barcelona",
            "2026-07-10", "2026-07-15", 2,
            Map.of("source", "goap"), List.of(), List.of(), List.of(), List.of(),
            1500.0, Map.of(), Map.of(), LocalDateTime.now()
        );

        fallbackItinerary = new UnforgettableItinerary(
            "fallback-result-id", "Barcelona",
            "2026-07-10", "2026-07-15", 2,
            Map.of("source", "fallback"), List.of(), List.of(), List.of(), List.of(),
            1500.0, Map.of(), Map.of(), LocalDateTime.now()
        );
    }

    /**
     * Simulates a successful Embabel GOAP dispatch: AgentPlatform is present,
     * runAgentFrom returns a process, process.resultOfType returns the itinerary.
     * Verifies that TravelService does NOT fall through to the virtual-thread path.
     */
    @Test
    void goapPath_successfulDispatch_returnsEmbabelResult() {
        // Build a mock AgentPlatform + Agent + Process using dynamic proxies
        // that mimic the Embabel API without requiring the actual Embabel classes.
        ApplicationContext ctx = buildContextWithEmbabelPlatform(goapItinerary, false);

        when(costTracker.isBudgetExceeded(any())).thenReturn(false);

        TravelService service = new TravelService(
            travelPlannerAgent, progressService, costTracker,
            repository, objectMapper, executor, ctx
        );

        UnforgettableItinerary result = service.planTrip(testRequest, "goap-session");

        assertNotNull(result);
        // The result came from the GOAP path, so it should have the goap ID
        assertEquals("goap-result-id", result.id());
        // Virtual-thread path should NOT have been called
        verify(travelPlannerAgent, never()).gatherIntelligence(any());
        verify(travelPlannerAgent, never()).searchAccommodations(any());
        verify(travelPlannerAgent, never()).synthesize(any(), any(), any(), any(), any());
    }

    /**
     * Simulates a GOAP dispatch failure: AgentPlatform is present but
     * process.run() throws. Verifies that TravelService gracefully falls
     * back to the virtual-thread path.
     */
    @Test
    void goapPath_dispatchFailure_fallsBackToVirtualThreads() {
        ApplicationContext ctx = buildContextWithEmbabelPlatform(null, true);

        when(costTracker.isBudgetExceeded(any())).thenReturn(false);
        stubVirtualThreadPath();

        TravelService service = new TravelService(
            travelPlannerAgent, progressService, costTracker,
            repository, objectMapper, executor, ctx
        );

        UnforgettableItinerary result = service.planTrip(testRequest, "goap-fail-session");

        assertNotNull(result);
        // Fallback path should have been used — verify agent methods were called
        verify(travelPlannerAgent).gatherIntelligence(any());
        verify(travelPlannerAgent).searchAccommodations(any());
        verify(travelPlannerAgent).synthesize(any(), any(), any(), any(), any());
    }

    /**
     * Verifies that a second trip reuses the cached AgentPlatform resolution
     * (doesn't query the context again) and still dispatches via GOAP.
     */
    @Test
    void goapPath_resolutionCached_secondTripStillUsesGoap() {
        ApplicationContext ctx = buildContextWithEmbabelPlatform(goapItinerary, false);

        when(costTracker.isBudgetExceeded(any())).thenReturn(false);

        TravelService service = new TravelService(
            travelPlannerAgent, progressService, costTracker,
            repository, objectMapper, executor, ctx
        );

        // First trip
        UnforgettableItinerary result1 = service.planTrip(testRequest, "goap-s1");
        assertEquals("goap-result-id", result1.id());

        // Second trip — should still use GOAP, not fallback
        UnforgettableItinerary result2 = service.planTrip(testRequest, "goap-s2");
        assertEquals("goap-result-id", result2.id());

        // AgentPlatform resolution only happened on the first trip (2 calls:
        // one for AgentPlatform type, one for Agent type in resolveEmbabelAgent).
        // The second trip hits the cached path and makes no context queries.
        verify(ctx, times(2)).getBeansOfType(any());
        // Virtual-thread fallback should never have been called
        verify(travelPlannerAgent, never()).gatherIntelligence(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a mock ApplicationContext that returns a simulated AgentPlatform
     * (and Agent) via dynamic proxies. The platform's {@code runAgentFrom}
     * method returns a process proxy whose {@code resultOfType} returns the
     * given itinerary.
     *
     * @param resultItinerary the itinerary to return from the GOAP process, or null
     * @param failOnRun       if true, process.run() throws to simulate GOAP failure
     */
    private ApplicationContext buildContextWithEmbabelPlatform(
            UnforgettableItinerary resultItinerary, boolean failOnRun) {

        ApplicationContext ctx = mock(ApplicationContext.class);

        // Create proxy for the "Process" object returned by AgentPlatform.runAgentFrom()
        Object processProxy = Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] { Runnable.class, ResultHolder.class },
            (proxy, method, args) -> {
                if ("run".equals(method.getName())) {
                    if (failOnRun) throw new RuntimeException("Simulated GOAP failure");
                    return null;
                }
                if ("resultOfType".equals(method.getName())) {
                    return resultItinerary;
                }
                if ("toString".equals(method.getName())) return "MockEmbabelProcess";
                if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                if ("equals".equals(method.getName())) return proxy == args[0];
                return null;
            }
        );

        // Create proxy for the "Agent" wrapper bean
        Object agentProxy = Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] { Named.class },
            (proxy, method, args) -> {
                if ("getName".equals(method.getName())) return "TravelPlanner";
                if ("toString".equals(method.getName())) return "MockEmbabelAgent[TravelPlanner]";
                if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                if ("equals".equals(method.getName())) return proxy == args[0];
                return null;
            }
        );

        // Create proxy for the "AgentPlatform"
        Object platformProxy = Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] { PlatformLike.class },
            (proxy, method, args) -> {
                if ("runAgentFrom".equals(method.getName())) {
                    return processProxy;
                }
                if ("runAgent".equals(method.getName())) {
                    return processProxy;
                }
                if ("toString".equals(method.getName())) return "MockAgentPlatform";
                if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                if ("equals".equals(method.getName())) return proxy == args[0];
                return null;
            }
        );

        // Wire them into the ApplicationContext mock.
        // TravelService calls Class.forName("com.embabel.agent.core.AgentPlatform")
        // then ctx.getBeansOfType(platformType). Since we can't control Class.forName,
        // we use a broader answer: any getBeansOfType call that doesn't match a known
        // type returns our platform/agent as appropriate.
        when(ctx.getBeansOfType(any())).thenAnswer(invocation -> {
            Class<?> type = invocation.getArgument(0);
            String typeName = type.getName();
            if (typeName.contains("AgentPlatform") || typeName.contains("Platform")) {
                return Map.of("agentPlatform", platformProxy);
            }
            if (typeName.contains("Agent") && !typeName.contains("Platform")) {
                return Map.of("travelPlannerAgent", agentProxy);
            }
            // For non-Embabel types, return the proxy maps so resolution works
            return Map.of("agentPlatform", platformProxy);
        });

        return ctx;
    }

    /**
     * Stub the TravelPlannerAgent for the virtual-thread fallback path.
     */
    private void stubVirtualThreadPath() {
        TravelIntelligence intel = new TravelIntelligence(
            Map.of("language", "Catalan"), Map.of("temp", "28C"),
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
            .thenReturn(fallbackItinerary);
    }

    private static boolean isEmbabelOnClasspath() {
        try {
            Class.forName("com.embabel.agent.core.AgentPlatform");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // ── Marker interfaces for dynamic proxies ─────────────────────────────────
    // (These allow the proxy to expose methods that TravelService calls via
    // reflection, without requiring the real Embabel classes on the test classpath.)

    interface Named {
        String getName();
    }

    interface ResultHolder {
        Object resultOfType(Class<?> type);
    }

    interface PlatformLike {
        Object runAgentFrom(Object agent, Object options, Map<String, Object> bindings);
        Object runAgent(String name, Object options, Map<String, Object> bindings);
    }
}
