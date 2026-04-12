package com.matoe.controller;

import com.matoe.entity.PromptVersionEntity;
import com.matoe.entity.SearchTargetEntity;
import com.matoe.repository.SearchTargetRepository;
import com.matoe.service.DynamicPromptService;
import com.matoe.service.LlmCostTrackingService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Admin dashboard REST API — prompt management, LLM cost monitoring,
 * search target configuration, and system status.
 */
/**
 * CORS is handled globally by {@link com.matoe.config.CorsConfig}. We do NOT
 * use {@code @CrossOrigin(origins = "*")} here because admin endpoints must
 * stay locked to the configured {@code matoe.cors.allowed-origins} whitelist.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    @Value("${matoe.admin.token:}")
    private String adminToken;

    private final DynamicPromptService promptService;
    private final LlmCostTrackingService costService;
    private final SearchTargetRepository searchTargetRepo;
    private final Environment env;

    public AdminController(DynamicPromptService promptService,
                           LlmCostTrackingService costService,
                           SearchTargetRepository searchTargetRepo,
                           Environment env) {
        this.promptService = promptService;
        this.costService = costService;
        this.searchTargetRepo = searchTargetRepo;
        this.env = env;
    }

    @PostConstruct
    void validateAuthPosture() {
        boolean isProduction = Arrays.asList(env.getActiveProfiles()).contains("docker")
                            || Arrays.asList(env.getActiveProfiles()).contains("prod");
        boolean tokenUnset = adminToken == null || adminToken.isBlank();
        if (isProduction && tokenUnset) {
            // Fail closed: refuse to start if deployed without an admin token
            throw new IllegalStateException(
                "MATOE_ADMIN_TOKEN is required in production (profile=docker/prod). " +
                "Set the env var before starting the stack. Admin mutation endpoints " +
                "will NOT start without a token to avoid accidental open-access deployments."
            );
        }
        if (tokenUnset) {
            log.warn("MATOE_ADMIN_TOKEN is unset — admin mutation endpoints are OPEN. " +
                     "This is only acceptable for local dev. Set MATOE_ADMIN_TOKEN for any shared deployment.");
        }
    }

    /**
     * Require a matching X-Admin-Token for mutations. Fails closed when the
     * token is unset AND the active profile is production — see
     * {@link #validateAuthPosture()}. In local dev, an unset token still
     * allows access to keep the DX friction low.
     */
    private void requireAuth(String token) {
        if (adminToken != null && !adminToken.isBlank()) {
            if (token == null || !token.equals(adminToken)) {
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid admin token");
            }
        }
    }

    // ── Prompt Management ─────────────────────────────────────────────────────

    @GetMapping("/prompts")
    public ResponseEntity<Map<String, Object>> getAllPrompts() {
        Set<String> agents = promptService.getAgentNames();
        List<Map<String, Object>> prompts = new ArrayList<>();
        for (String agent : agents) {
            prompts.add(Map.of(
                "agentName", agent,
                "activePrompt", promptService.getPrompt(agent),
                "historyCount", promptService.getHistory(agent).size()
            ));
        }
        return ResponseEntity.ok(Map.of("prompts", prompts));
    }

    @GetMapping("/prompts/{agentName}")
    public ResponseEntity<Map<String, Object>> getPromptDetail(@PathVariable String agentName) {
        return ResponseEntity.ok(Map.of(
            "agentName", agentName,
            "activePrompt", promptService.getPrompt(agentName),
            "history", promptService.getHistory(agentName)
        ));
    }

    @PostMapping("/prompts/{agentName}")
    public ResponseEntity<PromptVersionEntity> updatePrompt(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable String agentName,
            @RequestBody Map<String, String> body) {
        requireAuth(token);
        String promptText = body.get("promptText");
        String author = body.getOrDefault("author", "admin");
        if (promptText == null || promptText.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(promptService.savePrompt(agentName, promptText, author));
    }

    @PostMapping("/prompts/{agentName}/rollback/{version}")
    public ResponseEntity<PromptVersionEntity> rollbackPrompt(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable String agentName, @PathVariable int version) {
        requireAuth(token);
        return ResponseEntity.ok(promptService.rollback(agentName, version));
    }

    // ── Cost Monitoring ───────────────────────────────────────────────────────

    @GetMapping("/costs")
    public ResponseEntity<Map<String, Object>> getCostDashboard(
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(costService.getCostDashboard(hours));
    }

    @GetMapping("/costs/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSessionCost(@PathVariable String sessionId) {
        return ResponseEntity.ok(costService.getSessionCostSummary(sessionId));
    }

    // ── Search Target Management ──────────────────────────────────────────────

    @GetMapping("/search-targets")
    public ResponseEntity<List<SearchTargetEntity>> getAllSearchTargets() {
        return ResponseEntity.ok(searchTargetRepo.findAllByOrderByAgentNameAscPriorityAsc());
    }

    @GetMapping("/search-targets/{agentName}")
    public ResponseEntity<List<SearchTargetEntity>> getSearchTargets(@PathVariable String agentName) {
        return ResponseEntity.ok(
            searchTargetRepo.findByAgentNameAndEnabledTrueOrderByPriorityAsc(agentName));
    }

    @PostMapping("/search-targets")
    public ResponseEntity<SearchTargetEntity> addSearchTarget(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestBody SearchTargetEntity target) {
        requireAuth(token);
        return ResponseEntity.ok(searchTargetRepo.save(target));
    }

    @PutMapping("/search-targets/{id}")
    public ResponseEntity<SearchTargetEntity> updateSearchTarget(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable Long id, @RequestBody SearchTargetEntity target) {
        requireAuth(token);
        target.setId(id);
        return ResponseEntity.ok(searchTargetRepo.save(target));
    }

    @DeleteMapping("/search-targets/{id}")
    public ResponseEntity<Void> deleteSearchTarget(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable Long id) {
        requireAuth(token);
        searchTargetRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── System Status ─────────────────────────────────────────────────────────

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        return ResponseEntity.ok(Map.of(
            "status", "running",
            "agents", promptService.getAgentNames(),
            "searchTargets", searchTargetRepo.count(),
            "version", "0.1.0"
        ));
    }
}
