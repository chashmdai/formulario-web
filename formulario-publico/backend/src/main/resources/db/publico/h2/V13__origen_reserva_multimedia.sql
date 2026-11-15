-- HMAC del origen agrupado por OrigenCliente; nunca una dirección IP en claro.
-- Los recorridos anteriores comparten un grupo conservador al desconocerse su origen.
ALTER TABLE citizen_journey ADD COLUMN media_origin_ref CHAR(64) NOT NULL
 DEFAULT '0000000000000000000000000000000000000000000000000000000000000000';
CREATE INDEX ix_journey_media_origin ON citizen_journey(media_origin_ref);
CREATE INDEX ix_media_reservation ON media_upload(released,state,created_ms);
