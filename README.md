# M.A.T.O.E — Multi-Agent Travel Orchestration Engine

A multi-agent system that deploys 14 specialized AI agents to concurrently search, compare, and synthesize travel accommodations, transport, and attractions into 3-tier itinerary variants (Budget, Standard, Luxury) with day-by-day breakdowns.

## Architecture

- **Backend:** Spring Boot 3.2 on Java 21 (Virtual Threads / Project Loom)
- **Agent Framework:** [Embabel Agent 0.3.5](https://github.com/embabel/embabel-agent) — GOAP planning, multi-LLM routing
- **Frontend:** React 18 SPA with real-time SSE agent progress tracking
- **Database:** SQLite (local dev), PostgreSQL (Docker)
- **Web Scraping:** [browser-use](https://github.com/browser-use/browser-use) — 3 parallel Chromium instances load-balanced by nginx
- **Deployment:** Docker Compose (backend, frontend, browser pool, PostgreSQL, Redis)

## Agent Catalogue

| Agent | Function | Data Source |
|---|---|---|
| **Orchestrator** | Synthesizes 3-tier itinerary variants with day-by-day breakdown | Cloud LLM |
| **Country Specialist** | Regional travel intelligence (culture, safety, transit) | Browser + LLM |
| **Hotel Agent** | Hotel search (Booking.com, Hotels.com, Expedia) | Browser + LLM |
| **B&B Agent** | Bed-and-breakfast search | Browser + LLM |
| **Apartment Agent** | Holiday apartment/rental search | Browser + LLM |
| **Hostel Agent** | Budget accommodation search | Browser + LLM |
| **Flight Agent** | Flight search (Skyscanner, Google Flights, Kayak) | Browser + LLM |
| **Car/Bus Agent** | Ground transport (car rental + bus) | Browser + LLM |
| **Train Agent** | Train route search | Browser + LLM |
| **Ferry Agent** | Ferry route search | Browser + LLM |
| **Attractions Agent** | Attractions and experiences (Viator, GetYourGuide) | Browser + LLM |
| **Weather Agent** | Weather forecast for trip dates | LLM |
| **Currency Agent** | Currency, exchange rates, tipping customs | LLM |
| **Review Summary** | Traveller review sentiment synthesis | LLM |

All agents execute in parallel via Java Virtual Threads. Results are tagged with source provenance ("browser" or "llm").

## Quick Start

### Prerequisites

- Java 21+
- Node.js 16+

### Local Development

```bash
# Backend
cp .env.example .env          # Configure API keys (optional for local LLMs)
./gradlew bootRun             # Starts on http://localhost:8080/api

# Frontend (separate terminal)
cd frontend
npm install
npm run dev                   # Starts on http://localhost:3000
```

### Docker Compose

```bash
docker compose up -d          # Starts all services
docker compose logs -f        # Watch logs
docker compose down           # Stop
```

## Admin Dashboard

Access via the "Admin" tab in the frontend UI. Features:

- **Prompt Editor** — Edit agent prompts at runtime with version history and rollback
- **LLM Cost Monitoring** — Track spending by agent, model, and session with budget ceilings
- **Search Target Management** — Enable/disable search sites per agent with rate limits

REST API: `/api/admin/prompts`, `/api/admin/costs`, `/api/admin/search-targets`.

## Multi-LLM Routing

| Prefix | Provider | Example |
|---|---|---|
| `anthropic/` | Anthropic API | `anthropic/claude-3-5-sonnet` |
| `lmstudio/` | LM Studio (local) | `lmstudio/llama-3-8b` |
| `ollama/` | Ollama (local) | `ollama/mistral` |
| `openrouter/` | OpenRouter (cloud) | `openrouter/openai/gpt-4o` |

Configurable at three levels: per-request (frontend), per-agent (application.yml / admin), Embabel role-based (`embabel.models.llms.*`).

## Environment Variables

| Variable | Required | Purpose |
|---|---|---|
| `ANTHROPIC_API_KEY` | For cloud | Anthropic Claude access |
| `OPENROUTER_API_KEY` | For cloud | OpenRouter access |
| `LMSTUDIO_BASE_URL` | For local | LM Studio endpoint (default: `http://localhost:1234/v1`) |
| `OLLAMA_BASE_URL` | For local | Ollama endpoint (default: `http://localhost:11434/v1`) |

## API Endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/api/travel/plan` | POST | Plan a trip |
| `/api/travel/progress/{sessionId}` | GET (SSE) | Real-time agent progress stream |
| `/api/travel/itineraries` | GET | List saved itineraries |
| `/api/travel/health` | GET | Health check |
| `/api/admin/prompts` | GET/POST | Prompt management |
| `/api/admin/costs` | GET | LLM cost dashboard |
| `/api/admin/search-targets` | GET/POST/PUT/DELETE | Search target management |

## Running Tests

```bash
./gradlew test                                          # All tests
./gradlew test --tests com.matoe.domain.DomainModelTest # Single class
cd frontend && npm test                                 # Frontend tests
```
