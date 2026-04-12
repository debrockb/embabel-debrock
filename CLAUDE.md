# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**M.A.T.O.E** (Multi-Agent Travel Orchestration Engine) — a multi-agent system that takes user travel parameters (destination, dates, budget) and deploys specialized AI agents to concurrently search, compare, and synthesize accommodations and transport into a final itinerary.

## Tech Stack

- **Backend:** Spring Boot on Java 21+ (required for Virtual Threads / Project Loom)
- **Agent Framework:** [Embabel](https://embabel.com) — provides GOAP (Goal-Oriented Action Planning), `@Action`/`@Goal`/`@AchievesGoal` annotations, and dynamic LLM routing
- **Frontend:** SPA (React, Svelte, or Vue) communicating via REST + SSE/WebSockets
- **Database:** SQLite or PostgreSQL for agent states, past itineraries, cached results
- **Deployment:** Docker Compose on local NAS
- **Web Scraping:** Playwright Java (headless browser), rotating proxy pool, exponential backoff

## Architecture

### Agent System (Embabel Open Mode)

The system uses Embabel's Open mode where the **Orchestrator** sets a global `@Goal(description = "Plan an unforgettable trip")`. The GOAP planner fires agents in parallel when their preconditions are independent.

**Agent roles (14 agents):**
| Agent | Function |
|---|---|
| **Orchestrator** | Synthesizes 3-tier itinerary variants (Budget/Standard/Luxury) with day-by-day breakdowns |
| **Country Specialist** | Provides regional travel context (transit norms, local tips, visa, emergency) |
| **Hotel Agent** | Searches Booking.com, Hotels.com, Expedia via browser-use + LLM fallback |
| **B&B Agent** | Searches B&Bs and guesthouses |
| **Apartment Agent** | Searches short-term rentals by guest count |
| **Hostel Agent** | Budget accommodation search |
| **Flight Agent** | Finds air routes (fastest + cheapest) |
| **Car/Bus Agent** | Finds ground transit (rentals, coach options) |
| **Train Agent** | Train route search |
| **Ferry Agent** | Ferry route search |
| **Attractions Agent** | Finds attractions/experiences via Viator, GetYourGuide |
| **Weather Agent** | Weather forecast for trip dates |
| **Currency Agent** | Currency info, exchange rates, tipping customs |
| **Review Summary Agent** | Traveller review sentiment synthesis |

The Orchestrator uses a cloud LLM to synthesize all gathered data into 3 `ItineraryVariant` objects (Budget, Standard, Luxury), each with `ItineraryDay` breakdowns.

### Multi-LLM Routing

The system dynamically routes between local and cloud LLMs:
- **Local (cheap tasks):** LM Studio (`http://<ip>:1234/v1`, OpenAI-compatible), Ollama (via `embabel-agent-starter-ollama`)
- **Cloud (complex tasks):** Anthropic, OpenAI, OpenRouter — authenticated via env vars (`OPENROUTER_API_KEY`, etc.)

Model selection happens at two levels:
1. **Config-level defaults** in `application.yml` under `travel-agency.models.*`
2. **Per-request override** — the `TravelRequest` payload includes user-selected models, and the `@Action` method dynamically constructs the `Ai` client

### Concurrency Model

Java Virtual Threads (Project Loom) enable thousands of concurrent scraping threads without exhausting NAS memory. Accommodation agents and transport agents run in parallel since their preconditions are independent.

### Frontend Modules

1. **Mission Control Dashboard** — destination, dates, budget, travel style inputs; LLM provider/model selection dropdowns
2. **Live Agent Tracker** — real-time SSE feed showing agent status (deployed, scraping, extracted)
3. **Interactive Itinerary Canvas** — displays the final itinerary; user can reject individual components to trigger targeted agent re-runs
4. **Prompt & System Settings Panel** — admin view for editing system prompts, proxy settings, API keys at runtime

## Key Design Constraints

- **All agent outputs must be strongly typed** — use JVM records/classes with Jackson annotations (e.g., `AccommodationResult`). No unstructured text.
- **All prompts must be externalized** — stored in `application.yml` or DB under `travel-agency.prompts.*`, never hardcoded in Java source.
- **Zero recompilation for config changes** — model selection, prompts, proxy settings, and API keys must all be changeable at runtime.
- **Budget tiering is mandatory** — agents categorize results into user-defined tiers (Budget, Standard, Luxury).
- **NAS-safe resource limits** — JVM container must have strict memory caps (e.g., `deploy.resources.limits.memory: 4G`) to prevent OOM on the host.
- **Docker networking must resolve local IPs** — the container needs network bridging to reach LM Studio/Ollama on other machines on the LAN.

## Local Development Setup

### Prerequisites

- **Java 21+** (required for Virtual Threads / Project Loom)
- **Node.js 16+** (for frontend development)
- **.env file** — copy from `.env.example` and configure:

  ```bash
  cp .env.example .env
  ```

  - API keys are optional; if omitted, local LLM services (LM Studio/Ollama) will be used
  - Database URL can stay as SQLite default for local development
  - `LMSTUDIO_BASE_URL` and `OLLAMA_BASE_URL` must match your local service IPs on the LAN

### Running Locally (without Docker)

**Backend:**
```bash
# Run backend locally (requires Java 21+)
./gradlew bootRun

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests com.matoe.agents.HotelAgentTest

# Run a specific test method
./gradlew test --tests com.matoe.agents.HotelAgentTest.testExtractPrices

# Build JAR without running tests
./gradlew build -x test
```

**Frontend:**
```bash
cd frontend
npm install
npm run dev  # Starts dev server on http://localhost:3000
npm test     # Runs tests in watch mode (press 'a' to run all tests once)
```

The application uses:

- **Spring context-path:** `/api` — all endpoints are prefixed with `/api` (e.g., `/api/travel/health`)
- **Virtual Threads:** Automatically enabled via `spring.threads.virtual.enabled=true` in `application.yml`
- **Default database:** SQLite (`./data/matoe.db`) for local development
- **Frontend proxy:** `package.json` proxy points to `http://localhost:8080` for dev; nginx reverse-proxy in Docker
- **Admin dashboard:** `/api/admin/prompts`, `/api/admin/costs`, `/api/admin/search-targets` — accessible via "Admin" tab in frontend

## Build & Run (Docker Compose)

For production or isolated testing, use Docker Compose (requires Docker and Docker Compose):

```bash
# Start all services (backend, frontend, PostgreSQL database)
docker compose up -d

# Rebuild after code changes
docker compose up -d --build

# View logs
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f postgres

# Stop all services
docker compose down

# Clean up volumes (deletes PostgreSQL data)
docker compose down -v
```

**Important:** Docker Compose uses PostgreSQL (not SQLite). The backend environment variables are set in `docker-compose.yml`; API keys should be passed via environment on `docker compose` command or added to a `.env` file in the project root.

## Frontend Development

```bash
cd frontend

# Install dependencies
npm install

# Start dev server (runs on http://localhost:3000)
npm run dev

# Build for production
npm run build

# Run tests
npm run test
```

## Testing the System

**Quick health check:**
```bash
curl http://localhost:8080/api/travel/health
```

**Test trip planning (requires backend running):**
```bash
curl -X POST http://localhost:8080/api/travel/plan \
  -H "Content-Type: application/json" \
  -d '{
    "destination": "Paris",
    "startDate": "2024-06-01",
    "endDate": "2024-06-10",
    "guestCount": 2,
    "budgetMin": 2000,
    "budgetMax": 5000,
    "travelStyle": "standard",
    "accommodationTypes": ["hotel", "bb"],
    "transportTypes": ["flight"],
    "orchestratorModel": "anthropic/claude-3-5-sonnet",
    "extractorModel": "lmstudio/llama-3-8b"
  }'
```

## Project Structure

```text
src/main/java/com/matoe/
├── MATOEApplication.java               # Spring Boot entry point
├── config/CorsConfig.java              # CORS configuration
├── controller/
│   ├── TravelController.java           # REST endpoints (/travel/plan, /travel/health, SSE)
│   └── AdminController.java            # Admin API (prompts, costs, search targets)
├── service/
│   ├── TravelService.java              # Orchestration, parallel agent dispatch, persistence
│   ├── LlmService.java                 # Multi-provider LLM routing (Anthropic/LMStudio/Ollama/OpenRouter)
│   ├── BrowserAgentService.java        # Java client for browser-use Python service
│   ├── AgentProgressService.java       # SSE emitter management for real-time tracking
│   ├── PromptTemplateService.java      # {{variable}} substitution in prompt templates
│   ├── DynamicPromptService.java       # DB-backed prompt versioning (admin can edit at runtime)
│   └── LlmCostTrackingService.java     # Per-session cost tracking with budget ceilings
├── agents/                              # 14 specialist agents
│   ├── OrchestratorAgent.java          # LLM synthesis → 3 ItineraryVariants + day-by-day
│   ├── HotelAgent.java, BBAgent.java, ApartmentAgent.java, HostelAgent.java
│   ├── FlightAgent.java, CarBusAgent.java, TrainAgent.java, FerryAgent.java
│   ├── CountrySpecialistAgent.java, AttractionsAgent.java
│   ├── WeatherAgent.java, CurrencyAgent.java, ReviewSummaryAgent.java
├── domain/
│   ├── TravelRequest.java              # Multi-destination, interest tags, origin city
│   ├── AccommodationOption.java        # With source provenance (browser/llm)
│   ├── TransportOption.java            # With origin/destination fields
│   ├── AttractionOption.java           # Attractions/experiences
│   ├── ItineraryDay.java               # Day-by-day breakdown
│   ├── ItineraryVariant.java           # Budget/Standard/Luxury variant
│   └── UnforgettableItinerary.java     # Full itinerary with variants, weather, currency
├── entity/                              # JPA entities
│   ├── ItineraryEntity.java, PromptVersionEntity.java
│   ├── LlmCostLogEntity.java, SearchTargetEntity.java
└── repository/                          # Spring Data JPA repositories

frontend/src/
├── App.js                               # Main app with planner + admin tabs
├── components/
│   ├── MissionControl.js                # Trip form (origin, interests, transport types, LLM config)
│   ├── LiveAgentTracker.js              # Real-time SSE agent progress (14 agents)
│   ├── ItineraryCanvas.js               # Variants, day-by-day, weather, currency, attractions
│   └── AdminDashboard.js               # Prompt editor, cost monitoring, search target mgmt
```

## Configuration Structure (application.yml)

```yaml
travel-agency:
  models:
    default-orchestrator: "anthropic/claude-3-5-sonnet"
    default-extractor: "lmstudio/llama-3-8b"
  prompts:
    orchestrator: "You are an elite travel agent..."
    hotel-agent: "Extract price, rating, and location from the HTML..."
```

## Environment Variables

| Variable | Required? | Purpose | Default |
| --- | --- | --- | --- |
| `OPENROUTER_API_KEY` | No | OpenRouter cloud LLM access | — |
| `OPENAI_API_KEY` | No | OpenAI cloud LLM access | — |
| `ANTHROPIC_API_KEY` | No | Anthropic cloud LLM access | — |
| `LMSTUDIO_BASE_URL` | No | Local LM Studio endpoint | `http://localhost:1234/v1` |
| `OLLAMA_BASE_URL` | No | Local Ollama endpoint | `http://localhost:11434` |
| `SPRING_DATASOURCE_URL` | No* | Database connection | `jdbc:sqlite:./data/matoe.db` (local) |
| `SPRING_DATASOURCE_USERNAME` | No* | Database user | — |
| `SPRING_DATASOURCE_PASSWORD` | No* | Database password | — |

*Required only when using PostgreSQL in Docker; SQLite requires no authentication for local development.

**Setup for local development:**

```bash
cp .env.example .env
# Edit .env with your API keys (optional if using local LLMs only)
# Then source it if desired: source .env
```

For Docker, environment variables can be passed via:

```bash
# Option 1: .env file in project root (loaded automatically)
docker compose --env-file .env up -d

# Option 2: Override on command line
docker compose -e ANTHROPIC_API_KEY=sk-... up -d
```

## Database Initialization

### Local Development (SQLite)

SQLite is the default for local development. The database file is created automatically at `./data/matoe.db` on first run.

```bash
# To reset the database during local development:
rm -rf ./data/matoe.db
./gradlew bootRun  # Will recreate the schema
```

### Docker (PostgreSQL)

When using `docker compose up`, a PostgreSQL database is created automatically with:
- Database: `matoe`
- User: `matoe_user`
- Password: `matoe_password`

Data persists in the `postgres_data` volume.

```bash
# To reset PostgreSQL data in Docker:
docker compose down -v  # -v removes all volumes
docker compose up -d    # Recreates fresh database
```

### Flyway Migrations

Migration scripts are stored in `src/main/resources/db/migration/`. They follow Flyway naming conventions:
- `V1__initial_schema.sql`
- `V2__add_user_preferences.sql`

Run migrations automatically:

```bash
# Migrations run on application startup
./gradlew bootRun

# Or explicitly via Gradle:
./gradlew flywayMigrate
```

## Troubleshooting

### Backend won't start

**Error:** `java.net.ConnectException: Connection refused`

- Check if port 8080 is already in use: `lsof -i :8080`
- If using local LLMs (LM Studio/Ollama), verify they're running and accessible from your machine

**Error:** `Could not load JDBC Driver class 'org.sqlite.JDBC'`

- Ensure Java 21+ is installed: `java -version`
- Verify Gradle dependencies are downloaded: `./gradlew build`

### Frontend won't connect to backend

- Check that backend is running on `http://localhost:8080/api`
- Verify CORS is enabled in `application.yml` (should be by default)
- Check browser console for network errors
- In Docker, ensure both services are on the same network (`matoe-network`)

### Docker container crashes with OOM

- The JVM is limited to 4GB (`JAVA_TOOL_OPTIONS` in docker-compose.yml)
- If scraping many sites concurrently, reduce `max-concurrent-scrapes` in `application.yml`
- Monitor container memory: `docker stats`

### "No LLM response" errors

- If using cloud providers (Anthropic/OpenAI), verify API keys are set: `echo $ANTHROPIC_API_KEY`
- If using local LLMs, ensure the endpoint is correct in `.env` and the service is accessible from Docker: `docker compose exec backend curl http://lmstudio:1234/v1/models` (adjust IP if on different machine)

## Implementation Notes

### Adding a New Agent

1. Create a new class in `src/main/java/com/matoe/agents/` with `@Action` method
2. Annotate with preconditions and effects (GOAP will auto-wire dependencies)
3. Return a strongly-typed result (e.g., `List<AccommodationOption>`)
4. Add the prompt to `application.yml` under `travel-agency.prompts.*`
5. Wire the agent into `TravelService` for parallel execution

Example:
```java
@Component
public class MyAgent {
  @Action(
    name = "my_action",
    preconditions = {"some_condition"},
    effects = {"my_effect"}
  )
  public List<MyResult> search(TravelRequest request) {
    // Implementation
  }
}
```

### Tiering Logic

Accommodations and transport are tiered by `TravelService.tierAccommodations()` and `TravelService.tierTransport()` based on the budget range. Adjust the thresholds (currently 70% for budget, 130% for luxury) if needed.

### Adding a New LLM Provider

1. Add Embabel starter dependency to `build.gradle.kts`
2. Create config in `application.yml` with model mappings
3. Inject the `Ai` client in the agent using `@AiClient` or dynamic routing
4. Update the TravelController to accept the provider selection from frontend

### Frontend Real-Time Updates (Implemented)

SSE streaming is fully implemented:
- `AgentProgressService` manages `SseEmitter` connections per session
- `TravelService.planTrip()` broadcasts agent progress events during execution
- Frontend subscribes via `EventSource` to `/api/travel/progress/{sessionId}`
- `LiveAgentTracker.js` displays real-time status for all 14 agents

### Database Persistence (Implemented)

Itineraries are persisted in SQLite (local) or PostgreSQL (Docker):
- `ItineraryEntity` stores all data including JSON columns for nested collections
- `PromptVersionEntity` stores prompt version history for admin rollback
- `LlmCostLogEntity` tracks every LLM call with token counts and cost estimates
- `SearchTargetEntity` stores configurable search sites per agent
- Flyway migrations run automatically on startup (`V1__init.sql`)

### Admin Dashboard (Implemented)

Runtime configuration without recompilation:
- **Prompt Editor** at `/api/admin/prompts/{agentName}` — POST to save new version, version history with rollback
- **Cost Monitoring** at `/api/admin/costs` — spending by agent, model, and session
- **Search Targets** at `/api/admin/search-targets` — enable/disable sites per agent

## Common Patterns

### Parallelizing Agents

Use `CompletableFuture.supplyAsync()` in `TravelService.planTrip()` to run accommodation and transport agents in parallel:

```java
CompletableFuture<List<AccommodationOption>> hotelFuture = 
  CompletableFuture.supplyAsync(() -> hotelAgent.search(request));
CompletableFuture<List<TransportOption>> flightFuture = 
  CompletableFuture.supplyAsync(() -> flightAgent.search(request));

// Wait for both to complete
CompletableFuture.allOf(hotelFuture, flightFuture).join();
```

### Dynamic LLM Selection

The `TravelRequest` payload includes `orchestratorModel` and `extractorModel`. Inside an `@Action` method, construct the Ai client dynamically:

```java
Ai orchestrator = embabel.client(request.orchestratorModel());
String itinerary = orchestrator.prompt(systemPrompt).ask("Plan a trip to " + request.destination());
```

### Strongly-Typed Output

All agent responses must be strongly typed. Define records with Jackson annotations:

```java
public record HotelResult(
  @JsonProperty("name") String name,
  @JsonProperty("price_usd") double priceUsd,
  @JsonProperty("rating") double rating
) {}
```

Then parse/validate the response before returning:

```java
List<HotelResult> hotels = objectMapper.readValue(llmResponse, new TypeReference<List<HotelResult>>() {});
return hotels; // Type-safe
```

### Error Handling

Agents should gracefully degrade if a data source is unavailable. Return an empty list rather than crashing the entire itinerary:

```java
try {
  return scrapeHotels(url);
} catch (IOException e) {
  logger.warn("Hotel scrape failed; returning empty list", e);
  return Collections.emptyList(); // Don't fail the trip
}
```

### Budget Tiering

The `TravelService` includes `tierAccommodations()` and `tierTransport()` methods. These group results by price:

```java
// Thresholds (adjustable in application.yml or at runtime):
// Budget: 0–70% of max budget
// Standard: 70–130% of max budget
// Luxury: 130%+ of max budget
```

Adjust thresholds if the default split doesn't match user expectations.
