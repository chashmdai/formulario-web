-- Índices operativos: sin cambios a documentos, firmas, datos ni fases publicadas.
CREATE INDEX ix_submission_retention ON citizen_submission(state,content_retired,retention_checked_ms,created_ms,id);
CREATE INDEX ix_delivery_retention ON public_delivery(state,content_retired,retention_checked_ms,created_ms,id);
CREATE INDEX ix_custody_hold_phase ON custody_lifecycle(hold_active,phase,due_ms);
CREATE INDEX ix_retirement_phase ON storage_retirement(phase,submission_id);
