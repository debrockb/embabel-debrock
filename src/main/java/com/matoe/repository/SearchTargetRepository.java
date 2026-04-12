package com.matoe.repository;

import com.matoe.entity.SearchTargetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SearchTargetRepository extends JpaRepository<SearchTargetEntity, Long> {

    List<SearchTargetEntity> findByAgentNameAndEnabledTrueOrderByPriorityAsc(String agentName);

    List<SearchTargetEntity> findAllByOrderByAgentNameAscPriorityAsc();
}
