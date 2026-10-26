-- Metadatos operativos, fuera del documento/ACK firmados. Históricos sin fecha no se retrofechan.
ALTER TABLE citizen_submission ADD COLUMN first_ack_ms BIGINT;
ALTER TABLE public_delivery ADD COLUMN first_ack_ms BIGINT;
ALTER TABLE custody_lifecycle ADD COLUMN retirement_mode VARCHAR(40) NOT NULL DEFAULT 'GARANTIA_INDEPENDIENTE';
ALTER TABLE custody_lifecycle ADD COLUMN retirement_policy_ref VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE public_delivery ADD COLUMN retirement_mode VARCHAR(40) NOT NULL DEFAULT 'GARANTIA_INDEPENDIENTE';
ALTER TABLE public_delivery ADD COLUMN retirement_policy_ref VARCHAR(100) NOT NULL DEFAULT '';
