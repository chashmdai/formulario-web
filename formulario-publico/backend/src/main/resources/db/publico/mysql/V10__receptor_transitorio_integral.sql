-- El contenido íntegro vive solo durante la transferencia. Los comprobantes quedan fuera del payload retirado.
ALTER TABLE citizen_submission MODIFY COLUMN body MEDIUMTEXT NULL;
ALTER TABLE citizen_submission ADD COLUMN minimal_receipt TEXT;
ALTER TABLE citizen_submission ADD COLUMN content_retired BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public_delivery MODIFY COLUMN body TEXT NULL;
ALTER TABLE public_delivery ADD COLUMN minimal_receipt TEXT;
ALTER TABLE public_delivery ADD COLUMN content_retired BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public_delivery ADD COLUMN owner_fingerprint CHAR(64);
ALTER TABLE public_delivery ADD COLUMN retire_after_ms BIGINT NOT NULL DEFAULT 0;
ALTER TABLE public_delivery ADD COLUMN retention_hold BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE citizen_submission ADD COLUMN retention_checked_ms BIGINT NOT NULL DEFAULT 0;
ALTER TABLE public_delivery ADD COLUMN retention_checked_ms BIGINT NOT NULL DEFAULT 0;
CREATE TABLE custody_guarantee (
 queue_kind VARCHAR(16) NOT NULL,
 operation_id CHAR(36) NOT NULL,
 evidence TEXT NOT NULL,
 checked_ms BIGINT NOT NULL,
 expires_ms BIGINT NOT NULL,
 PRIMARY KEY(queue_kind,operation_id)
);
CREATE TABLE transient_maintenance (
 id INT PRIMARY KEY, lease_id CHAR(36), lease_until_ms BIGINT NOT NULL DEFAULT 0,
 last_ms BIGINT NOT NULL DEFAULT 0, last_code VARCHAR(40) NOT NULL DEFAULT 'CERRADO'
);
INSERT INTO transient_maintenance(id) VALUES(1);
