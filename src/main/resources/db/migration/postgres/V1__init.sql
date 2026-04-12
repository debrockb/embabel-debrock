-- M.A.T.O.E Initial Schema (PostgreSQL)

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

CREATE TABLE IF NOT EXISTS prompt_versions (
    id              SERIAL PRIMARY KEY,
    agent_name      VARCHAR(100) NOT NULL,
    prompt_text     TEXT NOT NULL,
    version         INTEGER NOT NULL DEFAULT 1,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(100) DEFAULT 'system'
);

CREATE INDEX IF NOT EXISTS idx_prompt_agent ON prompt_versions(agent_name, is_active);

CREATE TABLE IF NOT EXISTS llm_cost_log (
    id              SERIAL PRIMARY KEY,
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

CREATE TABLE IF NOT EXISTS search_targets (
    id              SERIAL PRIMARY KEY,
    agent_name      VARCHAR(100) NOT NULL,
    site_url        VARCHAR(500) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    priority        INTEGER NOT NULL DEFAULT 0,
    rate_limit_rpm  INTEGER DEFAULT 10,
    notes           VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_targets_agent ON search_targets(agent_name, enabled);

-- Seed default search targets so a fresh install shows sensible defaults in the
-- admin dashboard. These mirror the `travel-agency.browser.*` YAML defaults;
-- admins can enable/disable or reorder at runtime via the admin API.
INSERT INTO search_targets (agent_name, site_url, enabled, priority, rate_limit_rpm, notes) VALUES
  ('hotel-agent',       'booking.com',        TRUE, 1, 10, 'Primary hotel inventory'),
  ('hotel-agent',       'hotels.com',         TRUE, 2, 10, NULL),
  ('hotel-agent',       'expedia.com',        TRUE, 3, 10, NULL),
  ('bb-agent',          'booking.com',        TRUE, 1, 10, NULL),
  ('bb-agent',          'airbnb.com',         TRUE, 2, 10, NULL),
  ('bb-agent',          'bedandbreakfast.com',TRUE, 3, 10, NULL),
  ('apartment-agent',   'airbnb.com',         TRUE, 1, 10, NULL),
  ('apartment-agent',   'vrbo.com',           TRUE, 2, 10, NULL),
  ('apartment-agent',   'booking.com',        TRUE, 3, 10, NULL),
  ('hostel-agent',      'hostelworld.com',    TRUE, 1, 10, NULL),
  ('hostel-agent',      'booking.com',        TRUE, 2, 10, NULL),
  ('flight-agent',      'skyscanner.com',     TRUE, 1, 10, NULL),
  ('flight-agent',      'google.com/flights', TRUE, 2, 10, NULL),
  ('flight-agent',      'kayak.com',          TRUE, 3, 10, NULL),
  ('car-agent',         'rentalcars.com',     TRUE, 1, 10, NULL),
  ('car-agent',         'flixbus.com',        TRUE, 2, 10, NULL),
  ('train-agent',       'thetrainline.com',   TRUE, 1, 10, NULL),
  ('train-agent',       'omio.com',           TRUE, 2, 10, NULL),
  ('train-agent',       'seat61.com',         TRUE, 3, 10, NULL),
  ('ferry-agent',       'directferries.com',  TRUE, 1, 10, NULL),
  ('ferry-agent',       'aferry.com',         TRUE, 2, 10, NULL),
  ('country-specialist','lonelyplanet.com',   TRUE, 1, 10, NULL),
  ('country-specialist','wikivoyage.org',     TRUE, 2, 10, NULL),
  ('country-specialist','tripadvisor.com',    TRUE, 3, 10, NULL),
  ('attractions-agent', 'viator.com',         TRUE, 1, 10, NULL),
  ('attractions-agent', 'getyourguide.com',   TRUE, 2, 10, NULL),
  ('attractions-agent', 'tripadvisor.com',    TRUE, 3, 10, NULL),
  ('weather-agent',     'weather.com',        TRUE, 1, 10, NULL),
  ('weather-agent',     'accuweather.com',    TRUE, 2, 10, NULL),
  ('currency-agent',    'xe.com',             TRUE, 1, 10, NULL),
  ('currency-agent',    'oanda.com',          TRUE, 2, 10, NULL),
  ('review-summary-agent','tripadvisor.com',  TRUE, 1, 10, NULL),
  ('review-summary-agent','reddit.com/r/travel',TRUE,2,10, NULL);
