package com.matoe.repository;

import com.matoe.entity.AgentConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentConfigRepository extends JpaRepository<AgentConfigEntity, Long> {
    Optional<AgentConfigEntity> findByAgentName(String agentName);
    List<AgentConfigEntity> findAllByOrderByBuiltInDescAgentNameAsc();
    List<AgentConfigEntity> findByEnabledTrueOrderByAgentNameAsc();
}
