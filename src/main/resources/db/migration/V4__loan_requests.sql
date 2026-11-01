-- ====================================================
-- V4: solicitudes de préstamo (desembolso por YAPE o PLIN).
-- category y score son la foto del score al momento de la solicitud.
-- operation_code lo llena el supervisor al procesar el desembolso.
-- ====================================================
CREATE TABLE IF NOT EXISTS loan_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    phone VARCHAR(9) NOT NULL,
    deposit_type VARCHAR(4) NOT NULL CHECK (deposit_type IN ('YAPE', 'PLIN')),
    category VARCHAR(6) NOT NULL CHECK (category IN ('ORO', 'PLATA', 'BRONCE')),
    score INTEGER NOT NULL CHECK (score >= 0),
    requested_amount INTEGER NOT NULL CHECK (requested_amount > 0),
    status VARCHAR(10) NOT NULL DEFAULT 'PENDIENTE' CHECK (status IN ('PENDIENTE', 'PROCESADA', 'RECHAZADA')),
    operation_code VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_loan_request_user_id ON loan_request(user_id);
-- Listado del supervisor por estado, más recientes primero
CREATE INDEX idx_loan_request_status_created_at ON loan_request(status, created_at DESC);

-- Como máximo una solicitud PENDIENTE por usuario
CREATE UNIQUE INDEX uq_loan_request_user_pending ON loan_request(user_id) WHERE status = 'PENDIENTE';

CREATE TRIGGER trigger_loan_request_updated_at BEFORE UPDATE ON loan_request
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
