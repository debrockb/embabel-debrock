package com.matoe.repository;

import com.matoe.entity.PromptVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PromptVersionRepository extends JpaRepository<PromptVersionEntity, Long> {

    Optional<PromptVersionEntity> findByAgentNameAndActiveTrue(String agentName);

    List<PromptVersionEntity> findByAgentNameOrderByVersionDesc(String agentName);

    List<PromptVersionEntity> findByActiveTrueOrderByAgentName();
}
