package com.matoe.repository;

import com.matoe.entity.LlmCostLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LlmCostLogRepository extends JpaRepository<LlmCostLogEntity, Long> {

    List<LlmCostLogEntity> findBySessionIdOrderByCreatedAt(String sessionId);

    @Query("SELECT SUM(e.estimatedCost) FROM LlmCostLogEntity e WHERE e.sessionId = ?1")
    Double sumCostBySessionId(String sessionId);

    @Query("SELECT SUM(e.estimatedCost) FROM LlmCostLogEntity e WHERE e.createdAt >= ?1")
    Double sumCostSince(LocalDateTime since);

    @Query("SELECT e.agentName, COUNT(e), SUM(e.estimatedCost) FROM LlmCostLogEntity e " +
           "WHERE e.createdAt >= ?1 GROUP BY e.agentName ORDER BY SUM(e.estimatedCost) DESC")
    List<Object[]> costBreakdownByAgentSince(LocalDateTime since);

    @Query("SELECT e.model, COUNT(e), SUM(e.inputTokens), SUM(e.outputTokens), SUM(e.estimatedCost) " +
           "FROM LlmCostLogEntity e WHERE e.createdAt >= ?1 GROUP BY e.model ORDER BY SUM(e.estimatedCost) DESC")
    List<Object[]> costBreakdownByModelSince(LocalDateTime since);
}
