# M.A.T.O.E - Multi-Agent Travel Orchestration Engine

A hyper-parallelized, multi-agent travel planning system powered by Embabel and the JVM. Deploys specialized AI agents to concurrently search accommodations, flights, and ground transport, then synthesizes an unforgettable itinerary in minutes.

## Features

✈️ **Multi-Agent Architecture** — Orchestrator, Country Specialist, Hotel, B&B, Apartment, Flight, and Car/Bus agents working in parallel.

💡 **Dynamic LLM Routing** — Seamlessly swap between local models (LM Studio, Ollama) for cheap tasks and cloud models (Anthropic, OpenAI, OpenRouter) for complex reasoning.

🏆 **Budget Tiering** — Automatically categorize all findings into Budget, Standard, and Luxury tiers.

🌐 **Real-Time Frontend** — Interactive SPA with live agent tracking and the ability to customize the trip by rejecting individual components.

⚡ **JVM Virtual Threads** — Thousands of concurrent scraping threads on Java 21+.

🔒 **Data Sovereignty** — Run entirely on your local NAS; no recurring API costs for data extraction.

## Quick Start

### Prerequisites

- Java 21+
- Docker & Docker Compose
- Node.js 18+ (for local frontend dev)
- Git

### 1. Clone & Configure

```bash
cd "Travel bots"
cp .env.example .env
# Edit .env with your API keys
```

### 2. Run with Docker Compose

```bash
docker compose up -d
```

Backend starts on `http://localhost:8080/api`
Frontend starts on `http://localhost:3000`

### 3. Local Development (without Docker)

**Backend:**
```bash
./gradlew bootRun
```

**Frontend:**
```bash
cd frontend
npm install
npm run dev
```

## Project Structure

```
.
├── build.gradle.kts              # Gradle build config (Embabel, Spring Boot deps)
├── src/main/java/com/matoe/
│   ├── MATOEApplication.java      # Spring Boot entry point
│   ├── controller/                # REST endpoints
│   ├── service/                   # TravelService orchestrates agents
│   ├── agents/                    # @Action-annotated agent classes
│   └── domain/                    # Strongly-typed models (JVM records)
├── src/main/resources/
│   └── application.yml            # Externalized config, prompts, model selection
├── frontend/
│   ├── src/
│   │   ├── App.js                 # Main React app
│   │   └── components/
│   │       ├── MissionControl.js  # Trip input form
│   │       ├── LiveAgentTracker.js # Real-time agent status
│   │       └── ItineraryCanvas.js # Display final itinerary
│   ├── package.json
│   └── Dockerfile
├── docker-compose.yml             # Full stack orchestration
├── Dockerfile                     # Backend container
└── CLAUDE.md                      # Guidance for future Claude instances
```

## Agent Roles

| Agent | Responsibility | LLM Preference |
|-------|---|---|
| **Orchestrator** | Parses user intent, synthesizes final itinerary | Cloud (complex reasoning) |
| **Hotel Agent** | Scrapes hotel aggregators | Local (simple extraction) |
| **B&B Agent** | Finds guesthouses and boutique stays | Local |
| **Apartment Agent** | Searches short-term rentals | Local |
| **Flight Agent** | Queries flight databases | Local |
| **Car/Bus Agent** | Finds ground transport | Local |
| **Country Specialist** | Provides regional knowledge | Local or Cloud |

All agents execute **in parallel** thanks to Embabel's GOAP planner.

## Configuration

### Selecting Models

Edit `application.yml` to set default models:

```yaml
travel-agency:
  models:
    default-orchestrator: "anthropic/claude-3-5-sonnet"
    default-extractor: "lmstudio/llama-3-8b"
```

Or override per-request via the frontend "Mission Control" panel.

### Updating Prompts

All agent prompts are in `application.yml` under `travel-agency.prompts.*`. Restart the backend for changes to take effect, or use the admin panel (coming soon).

## API Endpoints

### POST `/api/travel/plan`

Plan a trip. Request body:

```json
{
  "destination": "Tokyo",
  "startDate": "2024-06-01",
  "endDate": "2024-06-10",
  "guestCount": 2,
  "budgetMin": 2000,
  "budgetMax": 5000,
  "travelStyle": "standard",
  "accommodationTypes": ["hotel", "bb", "apartment"],
  "transportTypes": ["flight", "car"],
  "orchestratorModel": "anthropic/claude-3-5-sonnet",
  "extractorModel": "lmstudio/llama-3-8b"
}
```

Response:

```json
{
  "id": "uuid",
  "destination": "Tokyo",
  "startDate": "2024-06-01",
  "endDate": "2024-06-10",
  "guestCount": 2,
  "regionInsights": { ... },
  "accommodations": [ ... ],
  "transport": [ ... ],
  "totalEstimatedCost": 3500.00,
  "createdAt": "2024-04-12T..."
}
```

### GET `/api/travel/itineraries`

Fetch all saved itineraries.

### GET `/api/travel/health`

Health check.

## Local LLM Setup (Optional)

To use local models, install **LM Studio** or **Ollama**, then set the base URLs in `.env`:

```bash
# LM Studio (default port 1234)
LMSTUDIO_BASE_URL=http://192.168.1.100:1234/v1

# Ollama (default port 11434)
OLLAMA_BASE_URL=http://192.168.1.100:11434
```

Docker networking will automatically resolve these on your LAN.

## Development

### Running Tests

```bash
./gradlew test
```

### Building for Production

```bash
docker compose -f docker-compose.yml build
docker compose -f docker-compose.yml up
```

### Debugging

Backend logs:
```bash
docker compose logs -f backend
```

Frontend logs:
```bash
docker compose logs -f frontend
```

## Roadmap

- [ ] Real-time SSE updates for agent progress
- [ ] WebSocket support for live itinerary editing
- [ ] Admin panel for prompt management
- [ ] Database persistence for past itineraries
- [ ] User authentication and account management
- [ ] Advanced scraper evasion (rotating proxies, headless browser)
- [ ] Multi-destination trip planning
- [ ] Integration with booking confirmation services

## Architecture Decisions

- **Embabel GOAP** enables dynamic agent orchestration without hardcoded orchestration logic.
- **Virtual Threads** handle massive concurrency without thread pool exhaustion.
- **Strongly-typed domain models** prevent JSON hallucinations and enforce schema compliance.
- **Externalized configuration** decouples prompts from code, enabling real-time updates.
- **Docker Compose on NAS** provides data sovereignty and local control.

## Troubleshooting

**Backend won't start:**
- Check Java version: `java -version` (must be 21+)
- Check PostgreSQL is running: `docker compose ps`
- Check logs: `docker compose logs backend`

**Frontend not connecting to backend:**
- Verify `REACT_APP_API_BASE_URL` in `.env`
- Check CORS settings in `application.yml`
- Check backend is running: `curl http://localhost:8080/api/travel/health`

**Agents returning empty results:**
- Verify LLM API keys are set (check logs)
- If using local models, verify LM Studio/Ollama is running
- Check agent scraping logic in `src/main/java/com/matoe/agents/`

## License

MIT

## Contact

For questions or contributions, open an issue or PR.
