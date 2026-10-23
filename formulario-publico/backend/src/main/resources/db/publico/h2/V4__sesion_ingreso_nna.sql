ALTER TABLE public_delivery ALTER COLUMN owner_run DROP NOT NULL;
ALTER TABLE public_delivery ADD COLUMN owner_session CHAR(64);
CREATE INDEX ix_delivery_session ON public_delivery(owner_session,id);
