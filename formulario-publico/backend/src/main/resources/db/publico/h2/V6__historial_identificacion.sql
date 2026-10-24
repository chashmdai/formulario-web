CREATE TABLE citizen_identity_history (
    id CHAR(36) PRIMARY KEY,
    journey_id CHAR(36) NOT NULL,
    person_id VARCHAR(64) NOT NULL,
    body CLOB NOT NULL,
    query_state VARCHAR(32) NOT NULL,
    retired_ms BIGINT NOT NULL,
    CONSTRAINT fk_identity_history_journey FOREIGN KEY(journey_id) REFERENCES citizen_journey(id)
);
CREATE INDEX ix_identity_history_journey ON citizen_identity_history(journey_id,person_id);
