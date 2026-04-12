package com.matoe.service;

import com.matoe.entity.SearchTargetEntity;
import com.matoe.repository.SearchTargetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Resolves search target sites for agents and enforces per-site rate limits.
 *
 * <p>DB-configured targets take precedence over YAML defaults. Admins can
 * enable/disable or reorder sites per agent at runtime via the admin API.
 *
 * <p><b>Rate limiting</b>: each target carries a {@code rateLimitRpm} (requests
 * per minute). {@link #getSites(String, String)} returns only the sites that
 * still have budget in the current 60-second window; exhausted sites are
 * skipped and logged. This prevents hot loops from getting the NAS IP blocked
 * by aggressive booking sites.
 */
@Service
public class SearchTargetService {

    private static final Logger log = LoggerFactory.getLogger(SearchTargetService.class);

    private final SearchTargetRepository searchTargetRepo;

    /** Rolling request counters keyed by site URL. Value is (windowStartMs, count). */
    private final Map<String, long[]> counters = new ConcurrentHashMap<>();
    /** Global fallback limit when a YAML-only site has no DB row. */
    private static final int DEFAULT_RPM_LIMIT = 30;

    public SearchTargetService(SearchTargetRepository searchTargetRepo) {
        this.searchTargetRepo = searchTargetRepo;
    }

    /**
     * Get active sites for an agent. Returns DB-configured sites if any exist,
     * otherwise falls back to the YAML default. In both cases, sites whose
     * {@code rateLimitRpm} budget has been exhausted in the current 60s window
     * are filtered out.
     *
     * @param agentName    e.g. "hotel-agent"
     * @param yamlDefault  comma-separated default from {@code @Value}
     */
    public List<String> getSites(String agentName, String yamlDefault) {
        List<SearchTargetEntity> dbTargets =
            searchTargetRepo.findByAgentNameAndEnabledTrueOrderByPriorityAsc(agentName);

        List<String> candidates;
        Map<String, Integer> limits = new HashMap<>();
        if (!dbTargets.isEmpty()) {
            candidates = dbTargets.stream()
                .map(SearchTargetEntity::getSiteUrl)
                .collect(Collectors.toList());
            for (SearchTargetEntity t : dbTargets) {
                Integer rpm = t.getRateLimitRpm();
                limits.put(t.getSiteUrl(), rpm == null || rpm <= 0 ? DEFAULT_RPM_LIMIT : rpm);
            }
        } else {
            candidates = Arrays.stream(yamlDefault.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
            for (String s : candidates) limits.put(s, DEFAULT_RPM_LIMIT);
        }

        // Rate-limit filter
        List<String> allowed = new ArrayList<>(candidates.size());
        for (String site : candidates) {
            int limit = limits.getOrDefault(site, DEFAULT_RPM_LIMIT);
            if (tryAcquire(site, limit)) {
                allowed.add(site);
            } else {
                log.warn("Rate limit hit for site '{}' (agent={}, limit={} rpm) — skipping this cycle",
                    site, agentName, limit);
            }
        }
        return allowed;
    }

    /**
     * Attempt to reserve one request slot for {@code siteUrl} in the current
     * 60s window. Returns {@code true} if the request is allowed; {@code false}
     * if the per-minute budget is exhausted.
     */
    private boolean tryAcquire(String siteUrl, int rpmLimit) {
        long now = System.currentTimeMillis();
        long windowMs = 60_000L;
        long[] bucket = counters.compute(siteUrl, (k, current) -> {
            if (current == null || now - current[0] >= windowMs) {
                return new long[]{now, 0L};
            }
            return current;
        });
        synchronized (bucket) {
            if (bucket[1] < rpmLimit) {
                bucket[1]++;
                return true;
            }
            return false;
        }
    }
}
