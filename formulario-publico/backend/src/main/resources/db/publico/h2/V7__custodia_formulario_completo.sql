CREATE TABLE citizen_submission (
    id CHAR(36) PRIMARY KEY,
    journey_id CHAR(36) NOT NULL UNIQUE,
    owner_session CHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    body CLOB NOT NULL,
    state VARCHAR(32) NOT NULL,
    created_ms BIGINT NOT NULL,
    lease_id CHAR(36),
    lease_until_ms BIGINT NOT NULL DEFAULT 0,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_ms BIGINT NOT NULL,
    last_error VARCHAR(40),
    ack CLOB,
    CONSTRAINT fk_submission_journey FOREIGN KEY(journey_id) REFERENCES citizen_journey(id)
);
CREATE INDEX ix_submission_pending ON citizen_submission(state,created_ms);
CREATE TABLE citizen_attachment (
    id CHAR(36) PRIMARY KEY,
    journey_id CHAR(36) NOT NULL,
    name VARCHAR(180) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    media_type VARCHAR(100) NOT NULL,
    content BLOB NOT NULL,
    CONSTRAINT fk_attachment_journey FOREIGN KEY(journey_id) REFERENCES citizen_journey(id)
);
CREATE INDEX ix_attachment_journey ON citizen_attachment(journey_id);
