package com.matoe.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity for persisting completed itineraries.
 * Nested collections (accommodations, transport, regional insights) are stored
 * as JSON text columns — they are always read together and never joined independently.
 */
@Entity
@Table(name = "itineraries", indexes = {
    @Index(name = "idx_itineraries_destination", columnList = "destination"),
    @Index(name = "idx_itineraries_created_at", columnList = "created_at")
})
public class ItineraryEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 255)
    private String destination;

    @Column(name = "start_date", nullable = false, length = 20)
    private String startDate;

    @Column(name = "end_date", nullable = false, length = 20)
    private String endDate;

    @Column(name = "guest_count", nullable = false)
    private int guestCount;

    @Column(name = "total_estimated_cost", nullable = false)
    private double totalEstimatedCost;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "region_insights_json", columnDefinition = "TEXT")
    private String regionInsightsJson;

    @Column(name = "accommodations_json", columnDefinition = "TEXT")
    private String accommodationsJson;

    @Column(name = "transport_json", columnDefinition = "TEXT")
    private String transportJson;

    // ── constructors ──────────────────────────────────────────────────────────

    public ItineraryEntity() {}

    public ItineraryEntity(String id, String destination, String startDate, String endDate,
                           int guestCount, double totalEstimatedCost, LocalDateTime createdAt,
                           String regionInsightsJson, String accommodationsJson, String transportJson) {
        this.id = id;
        this.destination = destination;
        this.startDate = startDate;
        this.endDate = endDate;
        this.guestCount = guestCount;
        this.totalEstimatedCost = totalEstimatedCost;
        this.createdAt = createdAt;
        this.regionInsightsJson = regionInsightsJson;
        this.accommodationsJson = accommodationsJson;
        this.transportJson = transportJson;
    }

    // ── getters / setters ─────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public int getGuestCount() { return guestCount; }
    public void setGuestCount(int guestCount) { this.guestCount = guestCount; }
    public double getTotalEstimatedCost() { return totalEstimatedCost; }
    public void setTotalEstimatedCost(double totalEstimatedCost) { this.totalEstimatedCost = totalEstimatedCost; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getRegionInsightsJson() { return regionInsightsJson; }
    public void setRegionInsightsJson(String regionInsightsJson) { this.regionInsightsJson = regionInsightsJson; }
    public String getAccommodationsJson() { return accommodationsJson; }
    public void setAccommodationsJson(String accommodationsJson) { this.accommodationsJson = accommodationsJson; }
    public String getTransportJson() { return transportJson; }
    public void setTransportJson(String transportJson) { this.transportJson = transportJson; }
}
