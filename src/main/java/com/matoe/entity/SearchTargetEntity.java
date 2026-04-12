package com.matoe.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "search_targets", indexes = {
    @Index(name = "idx_targets_agent", columnList = "agent_name, enabled")
})
public class SearchTargetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_name", nullable = false, length = 100)
    private String agentName;

    @Column(name = "site_url", nullable = false, length = 500)
    private String siteUrl;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private int priority;

    @Column(name = "rate_limit_rpm")
    private int rateLimitRpm = 10;

    @Column(length = 500)
    private String notes;

    public SearchTargetEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String v) { this.agentName = v; }
    public String getSiteUrl() { return siteUrl; }
    public void setSiteUrl(String v) { this.siteUrl = v; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public int getPriority() { return priority; }
    public void setPriority(int v) { this.priority = v; }
    public int getRateLimitRpm() { return rateLimitRpm; }
    public void setRateLimitRpm(int v) { this.rateLimitRpm = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
}
