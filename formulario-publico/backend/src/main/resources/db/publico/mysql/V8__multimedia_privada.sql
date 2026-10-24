ALTER TABLE citizen_submission ADD COLUMN document_version VARCHAR(8) NOT NULL DEFAULT '1.0';
ALTER TABLE citizen_attachment MODIFY COLUMN content LONGBLOB NULL;
ALTER TABLE citizen_attachment ADD COLUMN storage_kind VARCHAR(16) NOT NULL DEFAULT 'MYSQL';
ALTER TABLE citizen_attachment ADD COLUMN storage_key CHAR(36);
CREATE TABLE media_capacity (id INT PRIMARY KEY, reserved_bytes BIGINT NOT NULL);
INSERT INTO media_capacity VALUES(1,0);
CREATE TABLE media_upload (
 id CHAR(36) PRIMARY KEY,
 journey_id CHAR(36) NOT NULL,
 name VARCHAR(180) NOT NULL,
 expected_size BIGINT NOT NULL,
 expected_sha256 CHAR(64) NOT NULL,
 offset_bytes BIGINT NOT NULL DEFAULT 0,
 state VARCHAR(32) NOT NULL,
 lease_id CHAR(36), lease_until_ms BIGINT NOT NULL DEFAULT 0,
 created_ms BIGINT NOT NULL, touched_ms BIGINT NOT NULL, expires_ms BIGINT NOT NULL,
 media_type VARCHAR(100), view_kind VARCHAR(32), preview_sha256 CHAR(64), preview_size BIGINT,
 reserved_bytes BIGINT NOT NULL,
 error_code VARCHAR(40), released BOOLEAN NOT NULL DEFAULT FALSE,
 CONSTRAINT fk_media_journey FOREIGN KEY(journey_id) REFERENCES citizen_journey(id)
);
CREATE INDEX ix_media_owner ON media_upload(journey_id,state);
CREATE INDEX ix_media_expiry ON media_upload(state,expires_ms);
CREATE TABLE media_chunk (
 upload_id CHAR(36) NOT NULL, chunk_index INT NOT NULL, size_bytes BIGINT NOT NULL, sha256 CHAR(64) NOT NULL,
 PRIMARY KEY(upload_id,chunk_index), CONSTRAINT fk_media_chunk FOREIGN KEY(upload_id) REFERENCES media_upload(id)
);
CREATE TABLE media_access (
 id CHAR(36) PRIMARY KEY, attachment_id CHAR(36) NOT NULL, at_ms BIGINT NOT NULL,
 principal_kind VARCHAR(24) NOT NULL, principal_ref VARCHAR(64) NOT NULL,
 representation VARCHAR(24) NOT NULL, event_code VARCHAR(32) NOT NULL
);
CREATE INDEX ix_media_access_resource ON media_access(attachment_id,at_ms);
