"""
M.A.T.O.E Browser Service
--------------------------
A FastAPI microservice that wraps browser-use to give agents real web browsing capability.
Multiple concurrent browser sessions are managed via asyncio. Agents POST a search task;
the service launches a browser-use Agent, visits real websites, extracts structured data,
and returns JSON results.

Environment variables:
  ANTHROPIC_API_KEY   - Anthropic API key (preferred LLM for reasoning)
  OPENAI_API_KEY      - OpenAI API key (fallback)
  OPENROUTER_API_KEY  - OpenRouter key (fallback)
  DEFAULT_MODEL       - e.g. "anthropic/claude-3-5-sonnet-20241022"
  MAX_CONCURRENT      - Max parallel browser sessions (default: 5)
  BROWSER_HEADLESS    - Run browsers headless (default: true)
  LOG_LEVEL           - Logging level (default: INFO)
"""

import asyncio
import logging
import os
import time
from contextlib import asynccontextmanager
from typing import Any, Optional

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

# ── browser-use ──────────────────────────────────────────────────────────────
from browser_use import Agent as BrowserAgent
from browser_use.browser.browser import Browser, BrowserConfig

# ── LLM providers (langchain wrappers) ───────────────────────────────────────
from langchain_anthropic import ChatAnthropic
from langchain_openai import ChatOpenAI

# ── config ────────────────────────────────────────────────────────────────────
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
logging.basicConfig(level=LOG_LEVEL)
log = logging.getLogger("browser-service")

MAX_CONCURRENT = int(os.getenv("MAX_CONCURRENT", "5"))
BROWSER_HEADLESS = os.getenv("BROWSER_HEADLESS", "true").lower() == "true"
DEFAULT_MODEL = os.getenv("DEFAULT_MODEL", "anthropic/claude-3-5-sonnet-20241022")

ANTHROPIC_API_KEY = os.getenv("ANTHROPIC_API_KEY", "")
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")
OPENROUTER_API_KEY = os.getenv("OPENROUTER_API_KEY", "")

# Semaphore to cap concurrent browser sessions
_semaphore: asyncio.Semaphore = None  # initialised in lifespan


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _semaphore
    _semaphore = asyncio.Semaphore(MAX_CONCURRENT)
    log.info("Browser service starting — max concurrent sessions: %d", MAX_CONCURRENT)
    yield
    log.info("Browser service shutting down")


app = FastAPI(title="M.A.T.O.E Browser Service", version="1.0.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# ── request / response models ─────────────────────────────────────────────────

class BrowseRequest(BaseModel):
    """A single browsing task dispatched by a Java agent."""
    task: str = Field(..., description="Natural language task for the browser agent to perform")
    sites: list[str] = Field(default=[], description="Seed URLs to start from (optional)")
    extraction_schema: Optional[str] = Field(
        None,
        description="JSON schema hint for the extracted data (e.g. 'return a JSON array of {name, pricePerNight, ...}')"
    )
    model: Optional[str] = Field(None, description="LLM model override, e.g. 'anthropic/claude-3-5-sonnet-20241022'")
    max_steps: int = Field(default=15, description="Max browser steps before giving up")
    timeout_seconds: int = Field(default=120, description="Timeout for the whole browsing session")


class BrowseResponse(BaseModel):
    success: bool
    result: Any  # parsed JSON or raw text
    raw_text: Optional[str] = None
    steps_taken: int = 0
    duration_seconds: float = 0.0
    error: Optional[str] = None


class HealthResponse(BaseModel):
    status: str
    max_concurrent: int
    active_sessions: int
    default_model: str


# ── LLM factory ───────────────────────────────────────────────────────────────

def build_llm(model_string: Optional[str]):
    """Resolve a provider/model string to a LangChain chat model."""
    model_string = model_string or DEFAULT_MODEL

    if model_string.startswith("anthropic/"):
        model_name = model_string.split("/", 1)[1]
        # Map shorthand → versioned model
        model_map = {
            "claude-3-5-sonnet": "claude-3-5-sonnet-20241022",
            "claude-3-haiku": "claude-3-haiku-20240307",
            "claude-3-opus": "claude-3-opus-20240229",
            "claude-sonnet-4": "claude-sonnet-4-5",
            "claude-opus-4": "claude-opus-4-5",
        }
        resolved = model_map.get(model_name, model_name)
        return ChatAnthropic(
            model=resolved,
            api_key=ANTHROPIC_API_KEY,
            timeout=90,
            max_tokens=4096,
        )

    if model_string.startswith("openai/"):
        model_name = model_string.split("/", 1)[1]
        return ChatOpenAI(model=model_name, api_key=OPENAI_API_KEY, timeout=90)

    if model_string.startswith("openrouter/"):
        model_name = model_string.split("/", 1)[1]
        return ChatOpenAI(
            model=model_name,
            api_key=OPENROUTER_API_KEY,
            base_url="https://openrouter.ai/api/v1",
            timeout=90,
        )

    # fallback: treat as an Anthropic model name
    return ChatAnthropic(
        model=model_string,
        api_key=ANTHROPIC_API_KEY,
        timeout=90,
        max_tokens=4096,
    )


def parse_result(raw: str) -> Any:
    """Try to parse the agent output as JSON; fall back to raw string."""
    import json
    import re

    # Strip markdown fences
    cleaned = re.sub(r"```(?:json)?\s*", "", raw).strip().rstrip("```").strip()

    # Try full JSON parse
    try:
        return json.loads(cleaned)
    except json.JSONDecodeError:
        pass

    # Try extracting the first JSON array or object
    for pattern in (r"(\[.*?\])", r"(\{.*?\})"):
        match = re.search(pattern, cleaned, re.DOTALL)
        if match:
            try:
                return json.loads(match.group(1))
            except json.JSONDecodeError:
                pass

    return raw  # return as plain text if JSON parsing fails


# ── active session counter ─────────────────────────────────────────────────────
_active_sessions = 0


# ── endpoints ─────────────────────────────────────────────────────────────────

@app.get("/health", response_model=HealthResponse)
async def health():
    return HealthResponse(
        status="ok",
        max_concurrent=MAX_CONCURRENT,
        active_sessions=_active_sessions,
        default_model=DEFAULT_MODEL,
    )


@app.post("/browse", response_model=BrowseResponse)
async def browse(req: BrowseRequest):
    """
    Dispatch a browser-use agent to perform a real web search/extraction task.
    Blocks until the task completes (or times out).
    """
    global _active_sessions

    async with _semaphore:
        _active_sessions += 1
        start = time.time()
        try:
            llm = build_llm(req.model)

            # Build the full task description
            full_task = req.task
            if req.extraction_schema:
                full_task += f"\n\nReturn the result as: {req.extraction_schema}"
            if req.sites:
                full_task += f"\n\nStart by visiting these sites: {', '.join(req.sites)}"
            full_task += "\nReturn ONLY valid JSON, no markdown, no explanation."

            browser_cfg = BrowserConfig(headless=BROWSER_HEADLESS)
            browser = Browser(config=browser_cfg)
            try:
                agent = BrowserAgent(
                    task=full_task,
                    llm=llm,
                    browser=browser,
                    max_actions_per_step=4,
                )

                result_obj = await asyncio.wait_for(
                    agent.run(max_steps=req.max_steps),
                    timeout=req.timeout_seconds,
                )

                raw_text = str(result_obj.final_result()) if result_obj else ""
                parsed = parse_result(raw_text)
                duration = time.time() - start

                log.info("Browse task completed in %.1fs — task: %.80s", duration, req.task)
                return BrowseResponse(
                    success=True,
                    result=parsed,
                    raw_text=raw_text,
                    steps_taken=len(result_obj.history()) if result_obj else 0,
                    duration_seconds=round(duration, 2),
                )
            finally:
                await browser.close()

        except asyncio.TimeoutError:
            log.warning("Browse task timed out after %ds — task: %.80s", req.timeout_seconds, req.task)
            return BrowseResponse(
                success=False,
                result=None,
                error=f"Task timed out after {req.timeout_seconds}s",
                duration_seconds=round(time.time() - start, 2),
            )
        except Exception as exc:
            log.error("Browse task failed: %s — task: %.80s", exc, req.task, exc_info=True)
            return BrowseResponse(
                success=False,
                result=None,
                error=str(exc),
                duration_seconds=round(time.time() - start, 2),
            )
        finally:
            _active_sessions -= 1


@app.post("/browse/batch", response_model=list[BrowseResponse])
async def browse_batch(requests: list[BrowseRequest]):
    """
    Submit multiple browsing tasks at once. They run concurrently (up to MAX_CONCURRENT).
    Useful when a single agent wants to search multiple sites in parallel.
    """
    tasks = [browse(req) for req in requests]
    return await asyncio.gather(*tasks)
