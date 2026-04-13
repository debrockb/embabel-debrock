package com.matoe.service;

import com.matoe.entity.PromptVersionEntity;
import com.matoe.repository.PromptVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serves agent prompts dynamically — DB-stored prompts take precedence over YAML defaults.
 * Supports versioning so admins can roll back prompt changes.
 */
@Service
public class DynamicPromptService {

    private static final Logger log = LoggerFactory.getLogger(DynamicPromptService.class);

    private final PromptVersionRepository promptRepo;
    private final ConcurrentHashMap<String, String> yamlDefaults = new ConcurrentHashMap<>();

    public DynamicPromptService(PromptVersionRepository promptRepo) {
        this.promptRepo = promptRepo;
    }

    /** Register the YAML default for an agent (called at startup). */
    public void registerDefault(String agentName, String yamlPrompt) {
        yamlDefaults.put(agentName, yamlPrompt);
    }

    /** Get the active prompt, or the YAML default, or empty string. Used by admin seeding. */
    public String getPromptOrDefault(String agentName) {
        return yamlDefaults.getOrDefault(agentName, "");
    }

    /** Get the active prompt for an agent. DB version wins over YAML. */
    public String getPrompt(String agentName) {
        return promptRepo.findByAgentNameAndActiveTrue(agentName)
            .map(PromptVersionEntity::getPromptText)
            .orElseGet(() -> yamlDefaults.getOrDefault(agentName, ""));
    }

    /** Save a new prompt version (admin action). Deactivates the previous active version. */
    public PromptVersionEntity savePrompt(String agentName, String promptText, String author) {
        // Deactivate current active
        promptRepo.findByAgentNameAndActiveTrue(agentName)
            .ifPresent(prev -> { prev.setActive(false); promptRepo.save(prev); });

        // Determine next version number
        List<PromptVersionEntity> history = promptRepo.findByAgentNameOrderByVersionDesc(agentName);
        int nextVersion = history.isEmpty() ? 1 : history.get(0).getVersion() + 1;

        PromptVersionEntity entity = new PromptVersionEntity(agentName, promptText, nextVersion);
        entity.setCreatedBy(author != null ? author : "admin");
        entity.setCreatedAt(LocalDateTime.now());
        return promptRepo.save(entity);
    }

    /** Rollback to a previous version. */
    public PromptVersionEntity rollback(String agentName, int version) {
        List<PromptVersionEntity> history = promptRepo.findByAgentNameOrderByVersionDesc(agentName);
        // Deactivate all
        history.forEach(e -> { e.setActive(false); promptRepo.save(e); });
        // Activate the target version
        return history.stream()
            .filter(e -> e.getVersion() == version)
            .findFirst()
            .map(e -> { e.setActive(true); return promptRepo.save(e); })
            .orElseThrow(() -> new IllegalArgumentException("Version " + version + " not found for " + agentName));
    }

    /** Get full version history for an agent. */
    public List<PromptVersionEntity> getHistory(String agentName) {
        return promptRepo.findByAgentNameOrderByVersionDesc(agentName);
    }

    /** Get all active prompts. */
    public List<PromptVersionEntity> getAllActive() {
        return promptRepo.findByActiveTrueOrderByAgentName();
    }

    /** Get all registered agent names (both YAML and DB). */
    public Set<String> getAgentNames() {
        Set<String> names = new TreeSet<>(yamlDefaults.keySet());
        promptRepo.findByActiveTrueOrderByAgentName().forEach(e -> names.add(e.getAgentName()));
        return names;
    }
}
