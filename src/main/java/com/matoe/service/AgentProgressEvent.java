package com.matoe.service;

/**
 * Payload sent via SSE to the frontend for each agent status update.
 */
public record AgentProgressEvent(
    String agentName,
    String status,   // "idle" | "searching" | "analyzing" | "completed" | "error"
    int progress,    // 0–100
    String message   // human-readable detail
) {}
