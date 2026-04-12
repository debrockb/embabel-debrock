package com.matoe.controller;

import com.matoe.domain.TravelRequest;
import com.matoe.domain.UnforgettableItinerary;
import com.matoe.service.AgentProgressService;
import com.matoe.service.PdfExportService;
import com.matoe.service.TravelService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;
import java.util.Map;

/**
 * CORS is handled globally by {@link com.matoe.config.CorsConfig} (reads
 * {@code matoe.cors.allowed-origins}). We deliberately do NOT annotate this
 * class with {@code @CrossOrigin(origins = "*")} — that would override the
 * configured origin whitelist and weaken security.
 */
@RestController
@RequestMapping("/travel")
public class TravelController {

    private final TravelService travelService;
    private final AgentProgressService progressService;
    private final PdfExportService pdfExportService;

    public TravelController(TravelService travelService,
                            AgentProgressService progressService,
                            PdfExportService pdfExportService) {
        this.travelService = travelService;
        this.progressService = progressService;
        this.pdfExportService = pdfExportService;
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

    /** Download a saved itinerary as a printable PDF. */
    @GetMapping(value = "/itineraries/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String id) {
        UnforgettableItinerary it = travelService.getItinerary(id);
        if (it == null) return ResponseEntity.notFound().build();
        byte[] pdf = pdfExportService.render(it);
        String safeDest = it.destination() == null ? "itinerary"
            : it.destination().replaceAll("[^A-Za-z0-9_.-]", "_");
        String filename = "matoe-" + safeDest + "-" + id + ".pdf";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "service", "M.A.T.O.E",
            "version", "0.1.0"
        ));
    }
}
