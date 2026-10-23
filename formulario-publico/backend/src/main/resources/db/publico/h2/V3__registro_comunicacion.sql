ALTER TABLE public_delivery ADD COLUMN legacy_ack TEXT;
UPDATE public_delivery SET legacy_ack=ack;
UPDATE public_delivery SET state='PENDIENTE',ack=NULL,lease_id=NULL,lease_until_ms=0,next_attempt_ms=0,attempts=0,last_error=NULL WHERE ack IS NOT NULL;
