-- ====================================================
-- V6: préstamos (desembolso de una solicitud).
-- Una solicitud produce como máximo un préstamo y un depósito (operation_code) no se registra dos veces.
-- Sin ON DELETE CASCADE: un préstamo es un registro financiero y no debe borrarse en cascada.
-- ====================================================
CREATE TABLE IF NOT EXISTS loan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_request_id UUID NOT NULL REFERENCES loan_request(id),
    user_id UUID NOT NULL REFERENCES users(id),
    amount INTEGER NOT NULL CHECK (amount > 0),
    interest INTEGER NOT NULL CHECK (interest >= 0),
    interest_rate NUMERIC(5, 4) NOT NULL CHECK (interest_rate >= 0),
    total_amount INTEGER NOT NULL CHECK (total_amount = amount + interest),
    operation_code VARCHAR(50) NOT NULL,
    score INTEGER NOT NULL CHECK (score >= 0),
    category VARCHAR(6) NOT NULL CHECK (category IN ('ORO', 'PLATA', 'BRONCE')),
    deposit_type VARCHAR(4) NOT NULL CHECK (deposit_type IN ('YAPE', 'PLIN')),
    payment_date DATE NOT NULL,
    app_payment_date DATE NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'VIGENTE' CHECK (status IN ('VIGENTE', 'PAGADO', 'VENCIDO')),
    created_by VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_loan_loan_request_id UNIQUE (loan_request_id),
    CONSTRAINT uq_loan_operation_code UNIQUE (operation_code)
);

CREATE INDEX idx_loan_user_id ON loan(user_id);
CREATE INDEX idx_loan_status_payment_date ON loan(status, payment_date);

CREATE TRIGGER trigger_loan_updated_at BEFORE UPDATE ON loan
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
