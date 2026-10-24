CREATE TABLE citizen_journey (
    id CHAR(36) PRIMARY KEY,
    owner_session CHAR(64) NOT NULL,
    kind VARCHAR(24) NOT NULL,
    notice_version VARCHAR(100) NOT NULL,
    accepted_channel BOOLEAN NOT NULL,
    state VARCHAR(32) NOT NULL,
    query_count INT NOT NULL DEFAULT 0,
    created_ms BIGINT NOT NULL,
    expires_ms BIGINT NOT NULL
);
CREATE INDEX ix_journey_owner ON citizen_journey(owner_session,expires_ms);
CREATE TABLE citizen_person (
    journey_id CHAR(36) NOT NULL,
    person_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    body MEDIUMTEXT NOT NULL,
    query_state VARCHAR(32) NOT NULL,
    query_lease CHAR(36),
    lease_until_ms BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY(journey_id,person_id),
    CONSTRAINT fk_person_journey FOREIGN KEY(journey_id) REFERENCES citizen_journey(id)
);
CREATE TABLE citizen_abuse_bucket (
    bucket_id CHAR(64) PRIMARY KEY,
    window_ms BIGINT NOT NULL,
    starts INT NOT NULL,
    lookups INT NOT NULL
);
