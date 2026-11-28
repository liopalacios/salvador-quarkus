-- ====================================================
-- V12: rechazo de solicitudes de préstamo por el supervisor.
--  * POST /loan-requests/{id}/reject pasa una solicitud PENDIENTE a RECHAZADA, con motivo opcional.
--  * rejection_seen_at: el cliente ya vio la notificación del rechazo (deja de contar en la campana).
-- ====================================================
ALTER TABLE loan_request
    ADD COLUMN IF NOT EXISTS rejection_reason  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS rejected_at       TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rejected_by       VARCHAR(100),
    ADD COLUMN IF NOT EXISTS rejection_seen_at TIMESTAMPTZ;

-- Última solicitud del cliente (inicio y notificaciones)
CREATE INDEX IF NOT EXISTS idx_loan_request_user_created_at ON loan_request (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_loan_request_phone_created_at ON loan_request (phone, created_at DESC);
