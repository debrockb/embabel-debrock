-- M.A.T.O.E Initial Schema
-- Supports both SQLite and PostgreSQL

CREATE TABLE IF NOT EXISTS itineraries (
    id                      VARCHAR(36) PRIMARY KEY,
    destination             VARCHAR(255) NOT NULL,
    start_date              VARCHAR(20) NOT NULL,
    end_date                VARCHAR(20) NOT NULL,
    guest_count             INTEGER NOT NULL,
    total_estimated_cost    DOUBLE PRECISION NOT NULL,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    region_insights_json    TEXT,
    accommodations_json     TEXT,
    transport_json          TEXT,
    attractions_json        TEXT,
    variants_json           TEXT,
    weather_json            TEXT,
    currency_json           TEXT,
    request_json            TEXT
);

CREATE INDEX IF NOT EXISTS idx_itineraries_destination ON itineraries(destination);
CREATE INDEX IF NOT EXISTS idx_itineraries_created_at ON itineraries(created_at);

-- Prompt version history (admin can edit prompts at runtime)
CREATE TABLE IF NOT EXISTS prompt_versions (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    agent_name      VARCHAR(100) NOT NULL,
    prompt_text     TEXT NOT NULL,
    version         INTEGER NOT NULL DEFAULT 1,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(100) DEFAULT 'system'
);

CREATE INDEX IF NOT EXISTS idx_prompt_agent ON prompt_versions(agent_name, is_active);

-- LLM call cost tracking
CREATE TABLE IF NOT EXISTS llm_cost_log (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id      VARCHAR(100),
    agent_name      VARCHAR(100) NOT NULL,
    model           VARCHAR(200) NOT NULL,
    provider        VARCHAR(50) NOT NULL,
    input_tokens    INTEGER DEFAULT 0,
    output_tokens   INTEGER DEFAULT 0,
    estimated_cost  DOUBLE PRECISION DEFAULT 0.0,
    duration_ms     BIGINT DEFAULT 0,
    success         BOOLEAN NOT NULL DEFAULT TRUE,
    error_message   TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cost_session ON llm_cost_log(session_id);
CREATE INDEX IF NOT EXISTS idx_cost_agent ON llm_cost_log(agent_name);
CREATE INDEX IF NOT EXISTS idx_cost_created ON llm_cost_log(created_at);

-- Search target configuration (admin can change sites at runtime)
CREATE TABLE IF NOT EXISTS search_targets (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    agent_name      VARCHAR(100) NOT NULL,
    site_url        VARCHAR(500) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    priority        INTEGER NOT NULL DEFAULT 0,
    rate_limit_rpm  INTEGER DEFAULT 10,
    notes           VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_targets_agent ON search_targets(agent_name, enabled);
