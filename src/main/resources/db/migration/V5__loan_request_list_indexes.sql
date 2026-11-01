-- ====================================================
-- V5: índices para el listado del supervisor.
-- PENDIENTE se lista por created_at ascendente; PROCESADA y RECHAZADA por updated_at descendente.
-- (V4 ya creó idx_loan_request_status_created_at sobre (status, created_at DESC);
--  un índice B-tree se recorre en ambos sentidos, así que también sirve para el orden ascendente.)
-- ====================================================
CREATE INDEX IF NOT EXISTS idx_loan_request_status_updated_at ON loan_request(status, updated_at DESC);
