package com.matoe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Server-Sent Event (SSE) connections for real-time agent progress streaming.
 * Each trip planning session gets a unique sessionId; the frontend opens an EventSource
 * to /api/travel/progress/{sessionId} before posting the plan request.
 */
@Service
public class AgentProgressService {

    private static final Logger log = LoggerFactory.getLogger(AgentProgressService.class);

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public AgentProgressService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Register a new SSE emitter for a session. The frontend calls this endpoint
     * before dispatching the POST /plan request.
     */
    public SseEmitter subscribe(String sessionId) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5-minute timeout
        emitters.put(sessionId, emitter);

        emitter.onCompletion(() -> {
            emitters.remove(sessionId);
            log.debug("SSE completed for session {}", sessionId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(sessionId);
            log.debug("SSE timed out for session {}", sessionId);
        });
        emitter.onError(e -> {
            emitters.remove(sessionId);
            log.debug("SSE error for session {}: {}", sessionId, e.getMessage());
        });

        log.info("SSE subscriber registered: {}", sessionId);
        return emitter;
    }

    /**
     * Broadcast an agent progress event to the frontend.
     */
    public void broadcast(String sessionId, AgentProgressEvent event) {
        if (sessionId == null || sessionId.isBlank()) return;

        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) return;

        try {
            String json = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event()
                .name("agent-progress")
                .data(json)
                .id(String.valueOf(System.currentTimeMillis())));
        } catch (IOException e) {
            log.debug("SSE send failed for session {}, removing emitter", sessionId);
            emitters.remove(sessionId);
        }
    }

    /**
     * Signal that all agents have finished and close the SSE stream.
     */
    public void complete(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;

        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter == null) return;

        try {
            emitter.send(SseEmitter.event().name("complete").data("{\"status\":\"done\"}"));
            emitter.complete();
        } catch (IOException ignored) {
            // emitter may already be closed
        }
    }

    /**
     * Convenience: broadcast then update (avoids repeated sessionId null-checks in callers).
     */
    public void update(String sessionId, String agentName, String status, int progress, String message) {
        broadcast(sessionId, new AgentProgressEvent(agentName, status, progress, message));
    }
}
