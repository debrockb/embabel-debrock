package com.matoe.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Persisted agent configuration — stores both built-in agent overrides and
 * custom user-created agents. The admin dashboard reads/writes these to let
 * users configure the agent swarm without touching code.
 */
@Entity
@Table(name = "agent_configs")
public class AgentConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique agent name, e.g. "hotel-agent" or a user-defined name like "spa-agent". */
    @Column(name = "agent_name", nullable = false, unique = true, length = 100)
    private String agentName;

    /** Human-readable label for the UI. */
    @Column(length = 255)
    private String displayName;

    /** What this agent does (shown in the agent builder). */
    @Column(length = 1000)
    private String description;

    /** The prompt template with {{variable}} placeholders. */
    @Column(name = "prompt_template", columnDefinition = "TEXT")
    private String promptTemplate;

    /** Comma-separated search sites for browser-first path. */
    @Column(name = "search_sites", length = 1000)
    private String searchSites;

    /** Result type: "accommodation", "transport", "attraction", "intelligence". */
    @Column(name = "result_type", length = 50)
    private String resultType;

    /** Model role: "orchestrator" or "extractor". */
    @Column(name = "model_role", length = 50)
    private String modelRole;

    /** JSON schema hint for the expected result format (sent to browser/LLM). */
    @Column(name = "result_schema", columnDefinition = "TEXT")
    private String resultSchema;

    /** Whether this agent is active. Disabled agents are skipped. */
    @Column(nullable = false)
    private boolean enabled = true;

    /** Whether this is a built-in agent (false = user-created custom agent). */
    @Column(name = "built_in", nullable = false)
    private boolean builtIn = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(); }

    // ── Constructors ─────────────────────────────────────────────────────────

    public AgentConfigEntity() {}

    public AgentConfigEntity(String agentName, String displayName, String description,
                             String promptTemplate, String searchSites, String resultType,
                             String modelRole, String resultSchema, boolean enabled, boolean builtIn) {
        this.agentName = agentName;
        this.displayName = displayName;
        this.description = description;
        this.promptTemplate = promptTemplate;
        this.searchSites = searchSites;
        this.resultType = resultType;
        this.modelRole = modelRole;
        this.resultSchema = resultSchema;
        this.enabled = enabled;
        this.builtIn = builtIn;
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public Long getId() { return id; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPromptTemplate() { return promptTemplate; }
    public void setPromptTemplate(String promptTemplate) { this.promptTemplate = promptTemplate; }
    public String getSearchSites() { return searchSites; }
    public void setSearchSites(String searchSites) { this.searchSites = searchSites; }
    public String getResultType() { return resultType; }
    public void setResultType(String resultType) { this.resultType = resultType; }
    public String getModelRole() { return modelRole; }
    public void setModelRole(String modelRole) { this.modelRole = modelRole; }
    public String getResultSchema() { return resultSchema; }
    public void setResultSchema(String resultSchema) { this.resultSchema = resultSchema; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isBuiltIn() { return builtIn; }
    public void setBuiltIn(boolean builtIn) { this.builtIn = builtIn; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
