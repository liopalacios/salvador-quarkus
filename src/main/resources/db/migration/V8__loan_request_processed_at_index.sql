-- ====================================================
-- V8: listado del supervisor por pestaña.
--  * PENDIENTE  -> created_at ASC    (índice de V4: idx_loan_request_status_created_at)
--  * PROCESADA  -> processed_at DESC (índice nuevo)
--  * RECHAZADA  -> updated_at DESC   (índice de V5: idx_loan_request_status_updated_at; no hay fecha de rechazo)
-- Las solicitudes procesadas antes de V7 no tienen processed_at: se completa con updated_at
-- (deshabilitando el trigger para no pisar updated_at con la fecha de esta migración).
-- ====================================================
ALTER TABLE loan_request DISABLE TRIGGER trigger_loan_request_updated_at;

UPDATE loan_request
SET processed_at = updated_at
WHERE status = 'PROCESADA' AND processed_at IS NULL;

ALTER TABLE loan_request ENABLE TRIGGER trigger_loan_request_updated_at;

ALTER TABLE loan_request
    ADD CONSTRAINT ck_loan_request_processed_at CHECK (status <> 'PROCESADA' OR processed_at IS NOT NULL);

CREATE INDEX IF NOT EXISTS idx_loan_request_status_processed_at ON loan_request(status, processed_at DESC);
