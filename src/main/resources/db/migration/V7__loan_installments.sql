-- ====================================================
-- V7: préstamo en cuotas.
--  * loan_request guarda la frecuencia de cuota elegida (15 o 30 días) y cuándo se procesó.
--  * loan pasa a montos NUMERIC(12,2) (el interés puede tener céntimos) y guarda las
--    condiciones del plan, la fecha de registro de la operación y la de desembolso.
--  * loan_installment: cronograma de pagos para la cobranza.
-- Los préstamos anteriores (pago único) se completan con una sola cuota que vence en payment_date.
-- ====================================================

-- ---------- loan_request ----------
ALTER TABLE loan_request
    ADD COLUMN installment_frequency INTEGER CHECK (installment_frequency > 0),
    ADD COLUMN processed_at TIMESTAMPTZ;

-- GET /loans/current/phone/{phone}
CREATE INDEX IF NOT EXISTS idx_loan_request_phone_status ON loan_request(phone, status);

-- ---------- loan ----------
ALTER TABLE loan
    ALTER COLUMN amount TYPE NUMERIC(12, 2),
    ALTER COLUMN interest TYPE NUMERIC(12, 2),
    ALTER COLUMN total_amount TYPE NUMERIC(12, 2),
    ADD COLUMN phone VARCHAR(9),
    ADD COLUMN installment_frequency INTEGER,
    ADD COLUMN installment_count INTEGER,
    ADD COLUMN term_days INTEGER,
    ADD COLUMN operation_registered_at TIMESTAMPTZ,
    ADD COLUMN disbursement_date DATE,
    ADD COLUMN request_date TIMESTAMPTZ,
    ADD COLUMN outstanding_balance NUMERIC(12, 2);

-- Préstamos previos: pago único en payment_date
UPDATE loan l
SET phone                   = r.phone,
    request_date            = r.created_at,
    operation_registered_at = l.created_at,
    disbursement_date       = (l.created_at AT TIME ZONE 'America/Lima')::date,
    installment_count       = 1,
    outstanding_balance     = CASE WHEN l.status = 'PAGADO' THEN 0 ELSE l.total_amount END
FROM loan_request r
WHERE r.id = l.loan_request_id;

UPDATE loan
SET installment_frequency = GREATEST(payment_date - disbursement_date, 1),
    term_days             = GREATEST(payment_date - disbursement_date, 1);

ALTER TABLE loan
    ALTER COLUMN phone SET NOT NULL,
    ALTER COLUMN installment_frequency SET NOT NULL,
    ALTER COLUMN installment_count SET NOT NULL,
    ALTER COLUMN term_days SET NOT NULL,
    ALTER COLUMN operation_registered_at SET NOT NULL,
    ALTER COLUMN disbursement_date SET NOT NULL,
    ALTER COLUMN request_date SET NOT NULL,
    ALTER COLUMN outstanding_balance SET NOT NULL,
    ADD CONSTRAINT ck_loan_installment_frequency CHECK (installment_frequency > 0),
    ADD CONSTRAINT ck_loan_installment_count CHECK (installment_count > 0),
    ADD CONSTRAINT ck_loan_term_days CHECK (term_days > 0),
    ADD CONSTRAINT ck_loan_outstanding_balance CHECK (outstanding_balance >= 0 AND outstanding_balance <= total_amount);

CREATE INDEX IF NOT EXISTS idx_loan_phone_status ON loan(phone, status);

-- ---------- loan_installment ----------
CREATE TABLE IF NOT EXISTS loan_installment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id UUID NOT NULL REFERENCES loan(id),
    installment_number INTEGER NOT NULL CHECK (installment_number > 0),
    due_date DATE NOT NULL,
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    paid_amount NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (paid_amount >= 0 AND paid_amount <= amount),
    paid_at TIMESTAMPTZ,
    status VARCHAR(10) NOT NULL DEFAULT 'PENDIENTE' CHECK (status IN ('PENDIENTE', 'PAGADA', 'VENCIDA')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_loan_installment_loan_number UNIQUE (loan_id, installment_number)
);

-- Cobranza: cuotas por vencer / vencidas
CREATE INDEX IF NOT EXISTS idx_loan_installment_status_due_date ON loan_installment(status, due_date);

CREATE TRIGGER trigger_loan_installment_updated_at BEFORE UPDATE ON loan_installment
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

INSERT INTO loan_installment (loan_id, installment_number, due_date, amount, paid_amount, status, created_at, updated_at)
SELECT id, 1, payment_date, total_amount,
       CASE WHEN status = 'PAGADO' THEN total_amount ELSE 0 END,
       CASE status WHEN 'PAGADO' THEN 'PAGADA' WHEN 'VENCIDO' THEN 'VENCIDA' ELSE 'PENDIENTE' END,
       created_at, created_at
FROM loan;
