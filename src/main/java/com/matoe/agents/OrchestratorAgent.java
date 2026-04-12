package com.matoe.agents;

import com.matoe.annotations.AchievesGoal;
import com.matoe.annotations.Goal;
import com.matoe.domain.AccommodationOption;
import com.matoe.domain.TransportOption;
import com.matoe.domain.TravelRequest;
import com.matoe.domain.UnforgettableItinerary;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OrchestratorAgent {

    @Goal(description = "Plan an unforgettable trip based on user parameters")
    public void planTrip(TravelRequest request) {
        // Goal is declared; multiple agents work in parallel to achieve it
    }

    @AchievesGoal(goal = "Plan an unforgettable trip based on user parameters")
    public UnforgettableItinerary synthesizeItinerary(
        TravelRequest request,
        List<AccommodationOption> accommodations,
        List<TransportOption> transport,
        Map<String, Object> regionInsights
    ) {
        double totalCost = 0;
        if (accommodations != null) {
            totalCost += accommodations.stream()
                .mapToDouble(AccommodationOption::totalPrice)
                .sum();
        }
        if (transport != null) {
            totalCost += transport.stream()
                .mapToDouble(TransportOption::price)
                .sum();
        }

        return new UnforgettableItinerary(
            UUID.randomUUID().toString(),
            request.destination(),
            request.startDate().toString(),
            request.endDate().toString(),
            request.guestCount(),
            regionInsights != null ? regionInsights : Map.of(),
            accommodations != null ? accommodations : List.of(),
            transport != null ? transport : List.of(),
            totalCost,
            LocalDateTime.now()
        );
    }
}
