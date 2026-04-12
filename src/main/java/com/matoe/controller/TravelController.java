package com.matoe.controller;

import com.matoe.domain.TravelRequest;
import com.matoe.domain.UnforgettableItinerary;
import com.matoe.service.AgentProgressService;
import com.matoe.service.TravelService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;

@RestController
@RequestMapping("/travel")
@CrossOrigin(origins = "*")
public class TravelController {

    private final TravelService travelService;
    private final AgentProgressService progressService;

    public TravelController(TravelService travelService, AgentProgressService progressService) {
        this.travelService = travelService;
        this.progressService = progressService;
    }

    /** Open this EventSource BEFORE calling POST /plan with the same sessionId. */
    @GetMapping(value = "/progress/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToProgress(@PathVariable String sessionId) {
        return progressService.subscribe(sessionId);
    }

    /** POST /api/travel/plan?sessionId=xyz  (sessionId optional) */
    @PostMapping("/plan")
    public ResponseEntity<UnforgettableItinerary> planTrip(
            @RequestBody TravelRequest request,
            @RequestParam(required = false) String sessionId) {
        return ResponseEntity.ok(travelService.planTrip(request, sessionId));
    }

    @GetMapping("/itineraries")
    public ResponseEntity<List<UnforgettableItinerary>> getAllItineraries() {
        return ResponseEntity.ok(travelService.getAllItineraries());
    }

    @GetMapping("/itineraries/search")
    public ResponseEntity<List<UnforgettableItinerary>> searchItineraries(
            @RequestParam String destination) {
        return ResponseEntity.ok(travelService.searchItineraries(destination));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("M.A.T.O.E backend is running");
    }
}
