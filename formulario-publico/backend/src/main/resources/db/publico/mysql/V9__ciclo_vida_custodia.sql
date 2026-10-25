-- Estado operativo separado del documento firmado; sin cambios de canonicalización.
ALTER TABLE citizen_attachment ADD COLUMN bytes_retired BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE custody_lifecycle (
 submission_id CHAR(36) PRIMARY KEY,
 observed_ms BIGINT NOT NULL,
 verified_ms BIGINT,
 checked_ms BIGINT NOT NULL DEFAULT 0,
 due_ms BIGINT NOT NULL,
 ack_sha256 CHAR(64) NOT NULL,
 evidence_sha256 CHAR(64),
 hold_active BOOLEAN NOT NULL DEFAULT FALSE,
 phase VARCHAR(24) NOT NULL,
 last_error VARCHAR(40)
);
CREATE TABLE storage_retirement (
 attachment_id CHAR(36) PRIMARY KEY,
 journey_id CHAR(36) NOT NULL,
 submission_id CHAR(36),
 reason_code VARCHAR(32) NOT NULL,
 phase VARCHAR(24) NOT NULL,
 started_ms BIGINT NOT NULL,
 finished_ms BIGINT
);
CREATE INDEX ix_custody_due ON custody_lifecycle(phase,due_ms);
