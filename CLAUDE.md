# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**M.A.T.O.E** (Multi-Agent Travel Orchestration Engine) — a multi-agent system that deploys 14 specialized AI agents to concurrently search, compare, and synthesize travel accommodations, transport, and attractions into 3-tier itinerary variants (Budget, Standard, Luxury) with day-by-day breakdowns.

**GitHub:** https://github.com/debrockb/embabel-debrock
**Target deployment:** Docker Compose on a NAS (Portainer), 16GB RAM constraint.

## Tech Stack

- **Backend:** Spring Boot 3.2 on Java 21+ (Virtual Threads / Project Loom)
- **Agent Framework:** [Embabel Agent 0.3.5](https://github.com/embabel/embabel-agent) — GOAP planning, `@Agent`/`@Action`/`@AchievesGoal` annotations, `AgentPlatform`, multi-LLM routing via `Ai` interface
- **Frontend:** React 18 SPA with real-time SSE agent progress tracking
- **Database:** SQLite (local dev), PostgreSQL (Docker) — separate Flyway migrations per DB
- **Web Scraping:** [browser-use](https://github.com/browser-use/browser-use) Python service with Chromium, load-balanced by nginx
- **Deployment:** Docker Compose with backend, frontend (nginx), browser service, PostgreSQL, Redis

## Build & Run Commands

```bash
# Backend
./gradlew bootRun                    # Start (Java 21+ required)
./gradlew test                       # All tests
./gradlew test --tests com.matoe.domain.DomainModelTest  # Single class
./gradlew build -x test              # Build JAR

# Frontend
cd frontend && npm install && npm run dev   # Dev server on :3000
cd frontend && npm test                     # Tests
cd frontend && npm run build                # Production build

# Docker
docker compose up -d                 # Full stack
docker compose up -d --build         # Rebuild
docker compose logs -f backend       # Logs
docker compose down -v               # Teardown + delete volumes

# Health check
curl http://localhost:8080/api/travel/health
```

## Architecture

### Embabel GOAP Agent System

The `TravelPlannerAgent` (`src/main/java/com/matoe/agents/TravelPlannerAgent.java`) is the Embabel `@Agent` class. It uses GOAP (Goal-Oriented Action Planning) where the planner chains `@Action` methods by their **parameter types** (preconditions) and **return types** (effects):

```
TravelRequest (blackboard input)
  → gatherIntelligence(TravelRequest) → TravelIntelligence      ─┐
  → searchAccommodations(TravelRequest) → AccommodationResults    │ parallel (all need only TravelRequest)
  → searchTransport(TravelRequest) → TransportResults             │
  → searchAttractions(TravelRequest) → AttractionResults         ─┘
  → synthesize(TravelRequest, TravelIntelligence, AccommodationResults,
               TransportResults, AttractionResults) → UnforgettableItinerary  [GOAL]
```

Actions with independent preconditions run in parallel when `embabel.agent.platform.process-type=CONCURRENT`.

**Execution paths in `TravelService`:**
1. **Primary:** `AgentPlatform.runAgentFrom(travelPlannerAgent, ...)` — full Embabel GOAP planning
2. **Fallback:** Direct `CompletableFuture` dispatch through `TravelPlannerAgent` methods (if AgentPlatform unavailable)

### Embabel 0.3.5 API Reference

**Maven coordinates** (NOT on Maven Central — requires Embabel's Artifactory):
```
com.embabel.agent:embabel-agent-starter:0.3.5
com.embabel.agent:embabel-agent-starter-anthropic:0.3.5
com.embabel.agent:embabel-agent-starter-openai:0.3.5
com.embabel.agent:embabel-agent-starter-ollama:0.3.5
com.embabel.agent:embabel-agent-starter-lmstudio:0.3.5
com.embabel.agent:embabel-agent-starter-openai-custom:0.3.5  (OpenRouter, Groq, etc.)
com.embabel.agent:embabel-agent-starter-webmvc:0.3.5
com.embabel.agent:embabel-agent-starter-observability:0.3.5
com.embabel.agent:embabel-agent-test:0.3.5
```

**Repositories required in build.gradle.kts:**
```kotlin
maven { url = uri("https://repo.embabel.com/artifactory/libs-release") }
maven { url = uri("https://repo.embabel.com/artifactory/libs-snapshot") }
```

**Key annotations** (all in `com.embabel.agent.api.annotation`):

| Annotation | Target | Key Parameters | Purpose |
|---|---|---|---|
| `@Agent` | Class | `name`, `description`, `planner=PlannerType.GOAP` | Marks an agent, scanned by `AgentPlatform` |
| `@Action` | Method | `description`, `pre[]`, `post[]`, `cost`, `readOnly` | Declares a GOAP action. Parameter types = preconditions, return type = effect |
| `@AchievesGoal` | Method | `description` | Terminal action that achieves the agent's goal |
| `@Condition` | Method | `name` | Runtime condition check (returns boolean) |
| `@EmbabelComponent` | Class | `scan` | Contributes actions but is not an agent itself |

**Ai interface** (`com.embabel.agent.api.common.Ai`):
```java
ai.withLlm("model-name")           // by model ID
ai.withLlmByRole("orchestrator")   // by configured role
ai.withDefaultLlm()                // platform default
ai.withFirstAvailableLlmOf("a","b") // fallback chain
// Returns PromptRunner with: generateText(), createObject(), evaluateCondition()
```

**OperationContext** — injected into `@Action` methods as a parameter:
```java
@Action
public MyResult doWork(MyInput input, OperationContext ctx) {
    return ctx.ai().withLlm("gpt-4.1").createObject("prompt", MyResult.class);
}
```

**AgentPlatform** — injected as Spring bean (`@Autowired(required=false)` for graceful fallback):
```java
agentPlatform.runAgentFrom(agent, processOptions, Map.of("key", value))
process.run()           // run to completion
process.resultOfType(UnforgettableItinerary.class)  // typed result
```

**LLM config** in application.yml:
```yaml
embabel:
  models:
    default-llm: claude-3-5-sonnet-20241022
    llms:
      orchestrator: claude-3-5-sonnet-20241022
      extractor: llama-3-8b
  agent.platform.process-type: CONCURRENT
```

### Multi-LLM Routing

The `LlmService` routes calls by model-string prefix:

| Prefix | Provider | Endpoint |
|---|---|---|
| `anthropic/` | Anthropic Messages API | `anthropic.api.base-url` |
| `lmstudio/` | LM Studio (OpenAI-compatible) | `lmstudio.base-url` |
| `ollama/` | Ollama (OpenAI-compatible) | `ollama.base-url` |
| `openrouter/` | OpenRouter (OpenAI-compatible) | `openrouter.base-url` |

Each agent reads its model from `request.extractorModel()` (cheap tasks) or `request.orchestratorModel()` (synthesis). The frontend provides dropdowns for both.

### Agent Catalogue (14 agents)

All agents follow the same pattern: `@Component`, browser-first search via `BrowserAgentService`, LLM fallback via `LlmService`, cost tracking via `LlmCostTrackingService`, provenance tagging (`source="browser"` or `source="llm"`), dynamic prompts via `DynamicPromptService`, configurable search sites via `SearchTargetService`.

**Accommodation agents** (return `List<AccommodationOption>`):

| Agent | Type | YAML Prompt Key | Default Sites |
|---|---|---|---|
| `HotelAgent` | hotel | `hotel-agent` | booking.com, hotels.com, expedia.com |
| `BBAgent` | bb | `bb-agent` | booking.com, airbnb.com, bedandbreakfast.com |
| `ApartmentAgent` | apartment | `apartment-agent` | airbnb.com, vrbo.com, booking.com |
| `HostelAgent` | hostel | `hostel-agent` | hostelworld.com, booking.com |

**Transport agents** (return `List<TransportOption>`):

| Agent | Type | YAML Prompt Key | Default Sites |
|---|---|---|---|
| `FlightAgent` | flight | `flight-agent` | skyscanner.com, google.com/flights, kayak.com |
| `CarBusAgent` | car/bus | `car-agent` | rentalcars.com, flixbus.com |
| `TrainAgent` | train | `train-agent` | thetrainline.com, omio.com, seat61.com |
| `FerryAgent` | ferry | `ferry-agent` | directferries.com, aferry.com |

**Intelligence agents** (return `Map<String, Object>`):

| Agent | YAML Prompt Key | Data |
|---|---|---|
| `CountrySpecialistAgent` | `country-specialist` | Regional insights, visa, safety, transit |
| `WeatherAgent` | `weather-agent` | Temperature, precipitation, packing tips |
| `CurrencyAgent` | `currency-agent` | Exchange rates, tipping, ATM availability |
| `ReviewSummaryAgent` | `review-summary-agent` | Sentiment, pros/cons, safety rating |

**Special agents:**

| Agent | YAML Prompt Key | Returns |
|---|---|---|
| `AttractionsAgent` | `attractions-agent` | `List<AttractionOption>` |
| `OrchestratorAgent` | `orchestrator` | `UnforgettableItinerary` (with 3 `ItineraryVariant` + `ItineraryDay` breakdowns) |

### Adding a New Agent

1. Create `src/main/java/com/matoe/agents/MyAgent.java` as `@Component`
2. Inject: `BrowserAgentService`, `LlmService`, `ObjectMapper`, `PromptTemplateService`, `DynamicPromptService`, `LlmCostTrackingService`, `SearchTargetService`
3. Add `@Value("${travel-agency.prompts.my-agent}")` for the prompt
4. Add `@Value("${travel-agency.browser.my-sites:site1.com,site2.com}")` for sites
5. Add `@PostConstruct` to register default prompt with `DynamicPromptService`
6. Implement search method: browser-first → LLM fallback → cost tracking → provenance tagging
7. Add prompt to `application.yml` under `travel-agency.prompts.my-agent`
8. Add sites to `application.yml` under `travel-agency.browser.my-sites`
9. Add `buildMyPrompt()` to `PromptTemplateService`
10. Wire into `TravelPlannerAgent` (add field, constructor param, `@Action` method or call from existing)
11. Wire into `TravelService` fallback path if needed
12. Add agent to `INITIAL_AGENTS` array in `frontend/src/App.js`

### Domain Model

```
TravelRequest           → User input (destination, dates, guests, budget, styles, models, interests, origin)
AccommodationOption      → Hotel/BB/apartment/hostel result (with source provenance, imageUrl)
TransportOption          → Flight/car/bus/train/ferry result (with source, origin, destination)
AttractionOption         → Attraction/experience (category, tags, duration)
ItineraryDay             → Day-by-day: morning/afternoon/evening activities, meals, transport notes, cost
ItineraryVariant         → One of 3 tiers: accommodations, transport, attractions, dayByDay, highlights, tradeoffs
UnforgettableItinerary   → Final output: all above + regionInsights, weatherForecast, currencyInfo, variants
```

### Database

**SQLite** (local): `src/main/resources/db/migration/V1__init.sql` — uses `INTEGER PRIMARY KEY` (auto-increments in SQLite)
**PostgreSQL** (Docker): `src/main/resources/db/migration/postgres/V1__init.sql` — uses `SERIAL PRIMARY KEY`

Flyway location configured per profile:
- Default: `classpath:db/migration` (SQLite)
- Docker profile: `classpath:db/migration/postgres` (set in `application-docker.yml`)

**Tables:**
- `itineraries` — completed itineraries with JSON columns for nested data
- `prompt_versions` — prompt version history (admin rollback support)
- `llm_cost_log` — every LLM call with tokens, cost, duration, success/failure
- `search_targets` — per-agent search sites (admin can enable/disable at runtime)

### Services

| Service | Purpose |
|---|---|
| `TravelService` | Orchestration: Embabel GOAP primary path, CompletableFuture fallback, budget enforcement, tiering, persistence |
| `LlmService` | Multi-provider LLM routing by prefix (anthropic/, lmstudio/, ollama/, openrouter/) |
| `BrowserAgentService` | Java WebClient → Python browser-use service (health check, browse, batch) |
| `AgentProgressService` | SSE `SseEmitter` management per session |
| `PromptTemplateService` | `{{variable}}` substitution in prompt templates |
| `DynamicPromptService` | DB-backed prompts override YAML defaults; version history + rollback |
| `LlmCostTrackingService` | Per-session budget ceiling enforcement, cost dashboard queries |
| `SearchTargetService` | DB search targets override YAML site lists per agent |

### Frontend

| Component | Purpose |
|---|---|
| `App.js` | Main: planner/admin tabs, SSE connection, 14-agent INITIAL_AGENTS array |
| `MissionControl.js` | Trip form: destination, origin, dates, guests, budget, style, accommodation/transport types, interest tags, LLM model selection |
| `LiveAgentTracker.js` | Real-time SSE: status emoji, color, progress bar per agent |
| `ItineraryCanvas.js` | Results: variant selector, day-by-day timeline, weather/currency cards, attractions, accommodation/transport with tier compare, source badges, synthetic warning (no Book for `source=llm`) |
| `AdminDashboard.js` | Admin: prompt editor (version history), LLM cost monitoring (by agent/model), search target management (enable/disable/priority) |

### API Endpoints

| Endpoint | Method | Auth | Purpose |
|---|---|---|---|
| `/api/travel/plan?sessionId=x` | POST | None | Plan trip (body: `TravelRequest`) |
| `/api/travel/progress/{sessionId}` | GET (SSE) | None | Real-time agent progress stream |
| `/api/travel/itineraries` | GET | None | List saved itineraries |
| `/api/travel/itineraries/search?destination=x` | GET | None | Search itineraries |
| `/api/travel/health` | GET | None | Health check |
| `/api/admin/prompts` | GET | None | List all agent prompts |
| `/api/admin/prompts/{agentName}` | GET/POST | POST: `X-Admin-Token` | Get/update prompt |
| `/api/admin/prompts/{agentName}/rollback/{version}` | POST | `X-Admin-Token` | Rollback prompt |
| `/api/admin/costs?hours=24` | GET | None | Cost dashboard |
| `/api/admin/costs/session/{sessionId}` | GET | None | Session cost |
| `/api/admin/search-targets` | GET/POST | POST: `X-Admin-Token` | List/add search targets |
| `/api/admin/search-targets/{id}` | PUT/DELETE | `X-Admin-Token` | Update/delete target |
| `/api/admin/status` | GET | None | System status |

### Security

- **Admin mutations** require `X-Admin-Token` header matching `MATOE_ADMIN_TOKEN` env var. If env var is empty, auth is disabled (open access).
- **CORS** configured via `matoe.cors.allowed-origins` (default: `http://localhost:3000,http://localhost:80`)
- **Docker:** PostgreSQL and Redis ports are NOT exposed to the host (internal network only)
- **Synthetic data:** LLM-generated results tagged `source=llm`, Book links hidden in frontend, "AI-generated estimate" warning shown

### Docker / NAS Deployment

- **Spring profiles:** `SPRING_PROFILES_ACTIVE=docker` activates `application-docker.yml` (PostgreSQL dialect, driver, Flyway postgres location)
- **NAS networking:** `extra_hosts: ["host.docker.internal:host-gateway"]` on backend service for Linux Docker hosts
- **Memory budget:** Backend 2GB, browser 1GB (1 instance), PostgreSQL ~512MB, Redis ~256MB. Total ~4GB. Fits 16GB NAS.
- **Browser scaling:** Default 1 instance. Add `browser-2`, `browser-3` in docker-compose.yml and uncomment in `browser_service/nginx.conf` to scale.
- **Browser process safety:** `main.py` wraps browser agent in `try/finally` with `await browser.close()` to prevent Chromium leaks.

### Configuration (application.yml)

All prompts under `travel-agency.prompts.*` — use `{{variable}}` placeholders:
- `{{destination}}`, `{{startDate}}`, `{{endDate}}`, `{{guestCount}}`, `{{nights}}`, `{{days}}`
- `{{budgetMin}}`, `{{budgetMax}}`, `{{travelStyle}}`, `{{originCity}}`, `{{interestTags}}`

All browser sites under `travel-agency.browser.*` — comma-separated, overridable via admin dashboard.

Cost tracking: `travel-agency.cost-tracking.per-session-budget-usd` (default $2.00), `warn-at-percent` (default 80%).

### Environment Variables

| Variable | Purpose | Default |
|---|---|---|
| `ANTHROPIC_API_KEY` | Anthropic Claude | (empty) |
| `OPENROUTER_API_KEY` | OpenRouter | (empty) |
| `OPENAI_API_KEY` | OpenAI | (empty) |
| `LMSTUDIO_BASE_URL` | LM Studio endpoint | `http://localhost:1234/v1` |
| `OLLAMA_BASE_URL` | Ollama endpoint | `http://localhost:11434/v1` |
| `BROWSER_SERVICE_URL` | browser-use service | `http://localhost:8001` |
| `BROWSER_SERVICE_ENABLED` | Enable/disable browser | `true` |
| `MATOE_ADMIN_TOKEN` | Admin API auth token | (empty = no auth) |
| `MATOE_CORS_ORIGINS` | Allowed CORS origins | `http://localhost:3000,http://localhost:80` |
| `SPRING_PROFILES_ACTIVE` | Spring profile | (empty = SQLite) |

### Key Design Constraints

1. **All agent outputs strongly typed** — JVM records with Jackson annotations. No unstructured text.
2. **All prompts externalized** — `application.yml` or DB via `DynamicPromptService`. Never hardcoded.
3. **Zero recompilation for changes** — models, prompts, sites, API keys all configurable at runtime.
4. **Budget tiering mandatory** — 70% / 130% thresholds in `TravelService`. All results categorized Budget/Standard/Luxury.
5. **NAS-safe memory** — Backend capped at 2GB JVM, browser at 1GB. Total stack under 5GB.
6. **Source provenance** — Every result tagged `source=browser` or `source=llm`. Synthetic data clearly labelled.
7. **Graceful degradation** — Browser unavailable → LLM fallback. LLM fails → empty list (never crash the trip). Budget exceeded → skip synthesis, return raw results.

### Testing

**4 test classes, 54+ test methods:**
- `DomainModelTest` — Jackson round-trip serialization for all 7 domain records
- `LlmServiceTest` — Model routing (`resolveAnthropicModel`), JSON extraction, prefix stripping
- `PromptTemplateServiceTest` — Variable substitution for all agent prompt builders, edge cases
- `OrchestratorAgentTest` — Fallback variant generation, tier filtering, cost calculation

**Test config:** `src/test/resources/application.yml` — in-memory SQLite, Flyway disabled, `ddl-auto: create-drop`, browser disabled, placeholder API keys.

### Common Patterns

**Agent search method pattern:**
```java
public List<ResultType> search(TravelRequest request) {
    // 1. Try browser
    if (browserService.isAvailable()) {
        List<Map<String, Object>> raw = browserService.browseForList(task, sites, schema, model);
        if (raw != null && !raw.isEmpty()) return raw.stream().map(m -> map(m, "browser")).toList();
    }
    // 2. LLM fallback
    String prompt = dynamicPromptService.getPrompt("agent-name");
    String userPrompt = promptTemplateService.buildXPrompt(prompt, request);
    long start = System.currentTimeMillis();
    String raw = llmService.call(model, systemPrompt, userPrompt);
    costTracker.logCall(sessionId, "agent-name", model, provider, inputTokens, outputTokens, durationMs, true, null);
    return objectMapper.readValue(llmService.extractJson(raw), new TypeReference<>() {}).stream().map(m -> map(m, "llm")).toList();
    // 3. Empty fallback on failure
}
```

**Tiering:** `TravelService.tierAccommodations()` / `tierTransport()` — 70% of midpoint = budget line, 130% = luxury line.

**SSE:** `AgentProgressService.update(sessionId, agentName, status, progress, message)` — called from `TravelPlannerAgent` action methods.

**Dynamic prompts:** DB version (via admin dashboard) overrides YAML default. `DynamicPromptService.getPrompt("agent-name")` checks DB first.

**Search targets:** `SearchTargetService.getSites("agent-name", yamlDefault)` — DB targets override YAML when admin configures them.
