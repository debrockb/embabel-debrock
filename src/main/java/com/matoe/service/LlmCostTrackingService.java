package com.matoe.service;

import com.matoe.entity.LlmCostLogEntity;
import com.matoe.repository.LlmCostLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Tracks LLM call costs per session. Enforces per-session budget ceilings
 * and provides cost breakdown reports for the admin dashboard.
 */
@Service
public class LlmCostTrackingService {

    private static final Logger log = LoggerFactory.getLogger(LlmCostTrackingService.class);

    // Approximate per-token costs (USD) by provider — updated periodically
    private static final Map<String, double[]> TOKEN_COSTS = Map.of(
        "claude-3-5-sonnet",  new double[]{0.000003, 0.000015},   // input, output per token
        "claude-3-opus",      new double[]{0.000015, 0.000075},
        "claude-3-haiku",     new double[]{0.00000025, 0.00000125},
        "gpt-4o",             new double[]{0.000005, 0.000015},
        "gpt-4o-mini",        new double[]{0.00000015, 0.0000006},
        "llama-3-8b",         new double[]{0.0, 0.0},             // local = free
        "mistral-7b",         new double[]{0.0, 0.0},
        "default",            new double[]{0.000003, 0.000015}
    );

    private final LlmCostLogRepository costLogRepository;

    @Value("${travel-agency.cost-tracking.per-session-budget-usd:2.00}")
    private double perSessionBudgetUsd;

    @Value("${travel-agency.cost-tracking.warn-at-percent:80}")
    private int warnAtPercent;

    public LlmCostTrackingService(LlmCostLogRepository costLogRepository) {
        this.costLogRepository = costLogRepository;
    }

    public void logCall(String sessionId, String agentName, String model, String provider,
                        int inputTokens, int outputTokens, long durationMs, boolean success, String error) {
        double cost = estimateCost(model, inputTokens, outputTokens);

        LlmCostLogEntity entry = new LlmCostLogEntity();
        entry.setSessionId(sessionId);
        entry.setAgentName(agentName);
        entry.setModel(model);
        entry.setProvider(provider);
        entry.setInputTokens(inputTokens);
        entry.setOutputTokens(outputTokens);
        entry.setEstimatedCost(cost);
        entry.setDurationMs(durationMs);
        entry.setSuccess(success);
        entry.setErrorMessage(error);
        costLogRepository.save(entry);

        if (sessionId != null) checkBudget(sessionId, cost);
    }

    public boolean isBudgetExceeded(String sessionId) {
        if (sessionId == null) return false;
        Double total = costLogRepository.sumCostBySessionId(sessionId);
        return total != null && total >= perSessionBudgetUsd;
    }

    public Map<String, Object> getSessionCostSummary(String sessionId) {
        Double total = costLogRepository.sumCostBySessionId(sessionId);
        List<LlmCostLogEntity> entries = costLogRepository.findBySessionIdOrderByCreatedAt(sessionId);
        return Map.of(
            "sessionId", sessionId,
            "totalCostUsd", total != null ? total : 0.0,
            "budgetUsd", perSessionBudgetUsd,
            "callCount", entries.size()
        );
    }

    public Map<String, Object> getCostDashboard(int lastHours) {
        LocalDateTime since = LocalDateTime.now().minusHours(lastHours);
        Double totalCost = costLogRepository.sumCostSince(since);
        List<Object[]> byAgent = costLogRepository.costBreakdownByAgentSince(since);
        List<Object[]> byModel = costLogRepository.costBreakdownByModelSince(since);

        List<Map<String, Object>> agentBreakdown = new ArrayList<>();
        for (Object[] row : byAgent) {
            agentBreakdown.add(Map.of("agent", row[0], "calls", row[1], "cost", row[2]));
        }
        List<Map<String, Object>> modelBreakdown = new ArrayList<>();
        for (Object[] row : byModel) {
            modelBreakdown.add(Map.of("model", row[0], "calls", row[1],
                "inputTokens", row[2], "outputTokens", row[3], "cost", row[4]));
        }

        return Map.of(
            "periodHours", lastHours,
            "totalCostUsd", totalCost != null ? totalCost : 0.0,
            "byAgent", agentBreakdown,
            "byModel", modelBreakdown
        );
    }

    private double estimateCost(String model, int inputTokens, int outputTokens) {
        String key = TOKEN_COSTS.keySet().stream()
            .filter(k -> model.toLowerCase().contains(k)).findFirst().orElse("default");
        double[] rates = TOKEN_COSTS.get(key);
        return (inputTokens * rates[0]) + (outputTokens * rates[1]);
    }

    private void checkBudget(String sessionId, double addedCost) {
        Double total = costLogRepository.sumCostBySessionId(sessionId);
        if (total == null) return;
        double pct = (total / perSessionBudgetUsd) * 100;
        if (pct >= 100) {
            log.warn("Session {} EXCEEDED budget: ${} / ${}", sessionId, total, perSessionBudgetUsd);
        } else if (pct >= warnAtPercent) {
            log.warn("Session {} approaching budget: ${} / ${} ({}%)", sessionId, total, perSessionBudgetUsd, (int) pct);
        }
    }
}
