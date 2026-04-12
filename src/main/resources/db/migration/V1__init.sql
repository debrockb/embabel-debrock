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
    transport_json          TEXT
);

CREATE INDEX IF NOT EXISTS idx_itineraries_destination ON itineraries(destination);
CREATE INDEX IF NOT EXISTS idx_itineraries_created_at ON itineraries(created_at);
