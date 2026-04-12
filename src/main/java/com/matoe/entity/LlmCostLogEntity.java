package com.matoe.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "llm_cost_log", indexes = {
    @Index(name = "idx_cost_session", columnList = "session_id"),
    @Index(name = "idx_cost_agent", columnList = "agent_name"),
    @Index(name = "idx_cost_created", columnList = "created_at")
})
public class LlmCostLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "agent_name", nullable = false, length = 100)
    private String agentName;

    @Column(nullable = false, length = 200)
    private String model;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "input_tokens")
    private int inputTokens;

    @Column(name = "output_tokens")
    private int outputTokens;

    @Column(name = "estimated_cost")
    private double estimatedCost;

    @Column(name = "duration_ms")
    private long durationMs;

    @Column(nullable = false)
    private boolean success = true;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public LlmCostLogEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String v) { this.sessionId = v; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String v) { this.agentName = v; }
    public String getModel() { return model; }
    public void setModel(String v) { this.model = v; }
    public String getProvider() { return provider; }
    public void setProvider(String v) { this.provider = v; }
    public int getInputTokens() { return inputTokens; }
    public void setInputTokens(int v) { this.inputTokens = v; }
    public int getOutputTokens() { return outputTokens; }
    public void setOutputTokens(int v) { this.outputTokens = v; }
    public double getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(double v) { this.estimatedCost = v; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long v) { this.durationMs = v; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean v) { this.success = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { this.errorMessage = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
