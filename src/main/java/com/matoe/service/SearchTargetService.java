package com.matoe.service;

import com.matoe.repository.SearchTargetRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Resolves search target sites for agents.
 * DB-configured targets take precedence over YAML defaults.
 * Admin can enable/disable sites per agent at runtime.
 */
@Service
public class SearchTargetService {

    private final SearchTargetRepository searchTargetRepo;

    public SearchTargetService(SearchTargetRepository searchTargetRepo) {
        this.searchTargetRepo = searchTargetRepo;
    }

    /**
     * Get active sites for an agent. Returns DB-configured sites if any exist,
     * otherwise falls back to the YAML default.
     *
     * @param agentName  e.g. "hotel-agent"
     * @param yamlDefault  comma-separated default from @Value
     */
    public List<String> getSites(String agentName, String yamlDefault) {
        var dbTargets = searchTargetRepo.findByAgentNameAndEnabledTrueOrderByPriorityAsc(agentName);
        if (!dbTargets.isEmpty()) {
            return dbTargets.stream()
                .map(t -> t.getSiteUrl())
                .collect(Collectors.toList());
        }
        // Fall back to YAML
        return Arrays.asList(yamlDefault.split(","));
    }
}
