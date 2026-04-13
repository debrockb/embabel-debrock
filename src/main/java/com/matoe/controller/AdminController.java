package com.matoe.controller;

import com.matoe.entity.AgentConfigEntity;
import com.matoe.entity.PromptVersionEntity;
import com.matoe.entity.SearchTargetEntity;
import com.matoe.repository.AgentConfigRepository;
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
    private final AgentConfigRepository agentConfigRepo;
    private final Environment env;

    public AdminController(DynamicPromptService promptService,
                           LlmCostTrackingService costService,
                           SearchTargetRepository searchTargetRepo,
                           AgentConfigRepository agentConfigRepo,
                           Environment env) {
        this.promptService = promptService;
        this.costService = costService;
        this.searchTargetRepo = searchTargetRepo;
        this.agentConfigRepo = agentConfigRepo;
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
    public ResponseEntity<Map<String, Object>> getAllPrompts(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        requireAuth(token);
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
    public ResponseEntity<Map<String, Object>> getPromptDetail(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable String agentName) {
        requireAuth(token);
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
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam(defaultValue = "24") int hours) {
        requireAuth(token);
        return ResponseEntity.ok(costService.getCostDashboard(hours));
    }

    @GetMapping("/costs/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSessionCost(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable String sessionId) {
        requireAuth(token);
        return ResponseEntity.ok(costService.getSessionCostSummary(sessionId));
    }

    // ── Search Target Management ──────────────────────────────────────────────

    @GetMapping("/search-targets")
    public ResponseEntity<List<SearchTargetEntity>> getAllSearchTargets(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        requireAuth(token);
        return ResponseEntity.ok(searchTargetRepo.findAllByOrderByAgentNameAscPriorityAsc());
    }

    @GetMapping("/search-targets/{agentName}")
    public ResponseEntity<List<SearchTargetEntity>> getSearchTargets(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable String agentName) {
        requireAuth(token);
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

    // ── Agent Configuration ─────────────────────────────────────────────────

    /** List all agent configs (built-in + custom), seeding built-ins on first call. */
    @GetMapping("/agents")
    public ResponseEntity<List<AgentConfigEntity>> listAgents(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        requireAuth(token);
        seedBuiltInAgentsIfNeeded();
        return ResponseEntity.ok(agentConfigRepo.findAllByOrderByBuiltInDescAgentNameAsc());
    }

    /** Get a single agent config by ID. */
    @GetMapping("/agents/{id}")
    public ResponseEntity<AgentConfigEntity> getAgent(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable Long id) {
        requireAuth(token);
        return agentConfigRepo.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /** Create a new custom agent config. */
    @PostMapping("/agents")
    public ResponseEntity<AgentConfigEntity> createAgent(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestBody AgentConfigEntity config) {
        requireAuth(token);
        config.setBuiltIn(false); // user-created agents are never built-in
        AgentConfigEntity saved = agentConfigRepo.save(config);
        // Register the prompt so the dynamic prompt service picks it up
        if (config.getPromptTemplate() != null) {
            promptService.registerDefault(config.getAgentName(), config.getPromptTemplate());
        }
        return ResponseEntity.ok(saved);
    }

    /** Update an existing agent config (built-in or custom). */
    @PutMapping("/agents/{id}")
    public ResponseEntity<AgentConfigEntity> updateAgent(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable Long id,
            @RequestBody AgentConfigEntity update) {
        requireAuth(token);
        return agentConfigRepo.findById(id).map(existing -> {
            if (update.getDisplayName() != null) existing.setDisplayName(update.getDisplayName());
            if (update.getDescription() != null) existing.setDescription(update.getDescription());
            if (update.getPromptTemplate() != null) existing.setPromptTemplate(update.getPromptTemplate());
            if (update.getSearchSites() != null) existing.setSearchSites(update.getSearchSites());
            if (update.getResultType() != null) existing.setResultType(update.getResultType());
            if (update.getModelRole() != null) existing.setModelRole(update.getModelRole());
            if (update.getResultSchema() != null) existing.setResultSchema(update.getResultSchema());
            existing.setEnabled(update.isEnabled());
            AgentConfigEntity saved = agentConfigRepo.save(existing);
            // Sync prompt to DynamicPromptService
            if (saved.getPromptTemplate() != null) {
                promptService.registerDefault(saved.getAgentName(), saved.getPromptTemplate());
            }
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Delete a custom agent (built-in agents cannot be deleted). */
    @DeleteMapping("/agents/{id}")
    public ResponseEntity<Void> deleteAgent(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable Long id) {
        requireAuth(token);
        return agentConfigRepo.findById(id).map(agent -> {
            if (agent.isBuiltIn()) {
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Cannot delete built-in agents — disable them instead");
            }
            agentConfigRepo.deleteById(id);
            return ResponseEntity.noContent().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Seed the 14 built-in agent configs into the DB on first access. */
    private void seedBuiltInAgentsIfNeeded() {
        if (agentConfigRepo.count() > 0) return;
        List<AgentConfigEntity> builtIns = List.of(
            agent("hotel-agent", "Hotel Agent", "Search hotels", "accommodation", "extractor", "booking.com,hotels.com,expedia.com"),
            agent("bb-agent", "B&B Agent", "Search bed & breakfasts", "accommodation", "extractor", "booking.com,airbnb.com"),
            agent("apartment-agent", "Apartment Agent", "Search holiday apartments", "accommodation", "extractor", "airbnb.com,vrbo.com,booking.com"),
            agent("hostel-agent", "Hostel Agent", "Search hostels", "accommodation", "extractor", "hostelworld.com,booking.com"),
            agent("flight-agent", "Flight Agent", "Search flights", "transport", "extractor", "skyscanner.com,google.com/flights"),
            agent("car-agent", "Car Rental Agent", "Search car rentals", "transport", "extractor", "rentalcars.com,kayak.com/cars"),
            agent("bus-agent", "Bus Agent", "Search bus routes", "transport", "extractor", "flixbus.com,busbud.com"),
            agent("train-agent", "Train Agent", "Search train routes", "transport", "extractor", "thetrainline.com,omio.com"),
            agent("ferry-agent", "Ferry Agent", "Search ferry routes", "transport", "extractor", "directferries.com,aferry.com"),
            agent("attractions-agent", "Attractions Agent", "Search attractions and experiences", "attraction", "extractor", "viator.com,getyourguide.com"),
            agent("country-specialist", "Country Specialist", "Regional travel intelligence", "intelligence", "orchestrator", "lonelyplanet.com,tripadvisor.com"),
            agent("weather-agent", "Weather Agent", "Weather forecasts", "intelligence", "extractor", ""),
            agent("currency-agent", "Currency Agent", "Currency and exchange info", "intelligence", "extractor", ""),
            agent("review-summary-agent", "Review Agent", "Traveller review summaries", "intelligence", "extractor", "")
        );
        agentConfigRepo.saveAll(builtIns);
        log.info("Seeded {} built-in agent configs into database", builtIns.size());
    }

    private AgentConfigEntity agent(String name, String display, String desc, String resultType, String modelRole, String sites) {
        String prompt = promptService.getPromptOrDefault(name);
        return new AgentConfigEntity(name, display, desc, prompt, sites, resultType, modelRole, "", true, true);
    }

    // ── System Status ─────────────────────────────────────────────────────────

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        requireAuth(token);
        return ResponseEntity.ok(Map.of(
            "status", "running",
            "agents", promptService.getAgentNames(),
            "searchTargets", searchTargetRepo.count(),
            "agentConfigs", agentConfigRepo.count(),
            "version", "0.1.0"
        ));
    }
}
