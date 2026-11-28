-- ====================================================
-- V13: pagos de préstamos informados por el cliente y confirmados por el supervisor.
--  * POST /loans/{loanId}/payments: el cliente informa el pago de la próxima cuota (CUOTA) o del saldo (TOTAL)
--    con su código de operación. Queda PENDIENTE: el préstamo no cambia hasta que el supervisor lo confirme.
--  * POST /loan-payments/{id}/confirm marca las cuotas como pagadas y actualiza el saldo; /reject lo descarta.
-- ====================================================
CREATE TABLE IF NOT EXISTS loan_payment (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id          UUID          NOT NULL REFERENCES loan(id) ON DELETE CASCADE,
    user_id          UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    payment_type     VARCHAR(6)    NOT NULL CHECK (payment_type IN ('CUOTA', 'TOTAL')),
    installment_id   UUID          REFERENCES loan_installment(id) ON DELETE SET NULL,
    amount           NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    operation_code   VARCHAR(50)   NOT NULL,
    status           VARCHAR(10)   NOT NULL DEFAULT 'PENDIENTE' CHECK (status IN ('PENDIENTE', 'CONFIRMADO', 'RECHAZADO')),
    rejection_reason VARCHAR(255),
    reviewed_by      VARCHAR(100),
    reviewed_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Un solo pago pendiente por préstamo
CREATE UNIQUE INDEX IF NOT EXISTS uq_loan_payment_loan_pending ON loan_payment (loan_id) WHERE status = 'PENDIENTE';
-- Un código de operación no se puede reutilizar (salvo en pagos rechazados)
CREATE UNIQUE INDEX IF NOT EXISTS uq_loan_payment_operation_code ON loan_payment (operation_code) WHERE status <> 'RECHAZADO';
-- Listado del supervisor
CREATE INDEX IF NOT EXISTS idx_loan_payment_status_created_at ON loan_payment (status, created_at);
