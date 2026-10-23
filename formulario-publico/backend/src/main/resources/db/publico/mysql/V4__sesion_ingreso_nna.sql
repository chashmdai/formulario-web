ALTER TABLE public_delivery MODIFY COLUMN owner_run VARCHAR(10) NULL;
ALTER TABLE public_delivery ADD COLUMN owner_session CHAR(64);
CREATE INDEX ix_delivery_session ON public_delivery(owner_session,id);
