-- ====================================================
-- V14: motor de crédito de Salvador (C:\VUCE\Salvador.xlsx v3.0).
--  * credit_parameter: parámetros configurables con vigencia (TEA tope, mora, comisión, APIs, base de días).
--  * holiday: feriados nacionales; los vencimientos se corren al siguiente día hábil.
--  * loan / loan_installment: snapshot de tasas y desglose por cuota (interés, capital, comisión, APIs, saldos).
--  * Estados de cuota: ACTIVA (la próxima a pagar, solo una) | PAGADA | PENDIENTE.
--  * loan_payment: fecha de liquidación, desglose y detalle por cuota (loan_payment_item).
-- Los préstamos vigentes se recalculan con el motor al arrancar la app (LegacyLoanRecalculator).
-- ====================================================

-- ---------- Parámetros ----------
CREATE TABLE IF NOT EXISTS credit_parameter (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(30)    NOT NULL,
    param_value NUMERIC(20, 10) NOT NULL,
    valid_from  DATE           NOT NULL,
    valid_to    DATE,
    description VARCHAR(255),
    CONSTRAINT uq_credit_parameter_code_from UNIQUE (code, valid_from),
    CONSTRAINT ck_credit_parameter_range CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

INSERT INTO credit_parameter (code, param_value, valid_from, valid_to, description) VALUES
    ('TEA_TOPE',        1.1468, DATE '2026-11-01', DATE '2027-04-30', 'Tasa máxima legal BCRP (compensatoria, MN). Se actualiza cada 1 de mayo y 1 de noviembre'),
    ('TEA_MORA_FACTOR', 0.15,   DATE '2026-01-01', NULL,              'Tasa moratoria como fracción del tope (máximo legal 15%)'),
    ('COMISION_PCT',    0.30,   DATE '2026-01-01', NULL,              'Comisión de Salvador sobre el monto, repartida entre las cuotas'),
    ('APIS_MONTO',      5,      DATE '2026-01-01', NULL,              'Costo de APIs por crédito (S/), repartido entre las cuotas'),
    ('BASE_DIAS',       360,    DATE '2026-01-01', NULL,              'Días del año base (convención SBS)')
ON CONFLICT (code, valid_from) DO NOTHING;

-- ---------- Feriados ----------
CREATE TABLE IF NOT EXISTS holiday (
    holiday_date DATE PRIMARY KEY,
    description  VARCHAR(100) NOT NULL
);

INSERT INTO holiday (holiday_date, description) VALUES
    (DATE '2026-11-01', 'Todos los Santos'),
    (DATE '2026-12-08', 'Inmaculada Concepción'),
    (DATE '2026-12-09', 'Batalla de Ayacucho'),
    (DATE '2026-12-25', 'Navidad'),
    (DATE '2027-01-01', 'Año Nuevo'),
    (DATE '2027-03-25', 'Jueves Santo'),
    (DATE '2027-03-26', 'Viernes Santo'),
    (DATE '2027-05-01', 'Día del Trabajo'),
    (DATE '2027-06-07', 'Batalla de Arica y Día de la Bandera'),
    (DATE '2027-06-29', 'San Pedro y San Pablo'),
    (DATE '2027-07-23', 'Día de la Fuerza Aérea'),
    (DATE '2027-07-28', 'Fiestas Patrias'),
    (DATE '2027-07-29', 'Fiestas Patrias'),
    (DATE '2027-08-06', 'Batalla de Junín'),
    (DATE '2027-08-30', 'Santa Rosa de Lima'),
    (DATE '2027-10-08', 'Combate de Angamos'),
    (DATE '2027-11-01', 'Todos los Santos'),
    (DATE '2027-12-08', 'Inmaculada Concepción'),
    (DATE '2027-12-09', 'Batalla de Ayacucho'),
    (DATE '2027-12-25', 'Navidad')
ON CONFLICT (holiday_date) DO NOTHING;

-- ---------- Préstamo: snapshot del motor ----------
-- El total ahora incluye comisión y APIs (total = n × cuota): se quita el CHECK total = monto + interés.
DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'loan'::regclass AND contype = 'c'
               AND pg_get_constraintdef(oid) ILIKE '%total_amount%' AND pg_get_constraintdef(oid) ILIKE '%interest%'
    LOOP
        EXECUTE format('ALTER TABLE loan DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

ALTER TABLE loan
    ADD COLUMN IF NOT EXISTS modality           VARCHAR(10),
    ADD COLUMN IF NOT EXISTS tea                NUMERIC(10, 6),
    ADD COLUMN IF NOT EXISTS ted                NUMERIC(30, 25),
    ADD COLUMN IF NOT EXISTS ted_mora           NUMERIC(30, 25),
    ADD COLUMN IF NOT EXISTS commission_pct     NUMERIC(6, 4),
    ADD COLUMN IF NOT EXISTS apis_amount        NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS commission_total   NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS apis_total         NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS installment_amount NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS tcea               NUMERIC(20, 10),
    ADD COLUMN IF NOT EXISTS engine_version     INTEGER,
    ADD COLUMN IF NOT EXISTS closed_at          TIMESTAMPTZ;

ALTER TABLE loan ADD CONSTRAINT ck_loan_modality CHECK (modality IS NULL OR modality IN ('QUINCENAL', 'MENSUAL'));

-- Último préstamo del usuario (regla de 48 h para volver a solicitar)
CREATE INDEX IF NOT EXISTS idx_loan_user_closed_at ON loan (user_id, closed_at DESC);

-- ---------- Cuotas: desglose y estados ACTIVA / PAGADA / PENDIENTE ----------
DO $$
DECLARE c RECORD;
BEGIN
    -- CHECK del estado y de paid_amount <= amount (un pago con atraso puede superar la cuota del contrato)
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'loan_installment'::regclass AND contype = 'c'
               AND (pg_get_constraintdef(oid) ILIKE '%status%' OR pg_get_constraintdef(oid) ILIKE '%paid_amount%')
    LOOP
        EXECUTE format('ALTER TABLE loan_installment DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

ALTER TABLE loan_installment
    ADD COLUMN IF NOT EXISTS t_days          INTEGER,
    ADD COLUMN IF NOT EXISTS t_prev_days     INTEGER,
    ADD COLUMN IF NOT EXISTS opening_balance NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS interest        NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS principal       NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS commission      NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS apis            NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS closing_balance NUMERIC(12, 2);

-- Datos actuales: VENCIDA pasa a no pagada; la primera no pagada de cada préstamo queda ACTIVA
UPDATE loan_installment SET status = 'PENDIENTE' WHERE status = 'VENCIDA';
UPDATE loan_installment i SET status = 'ACTIVA'
FROM (
    SELECT DISTINCT ON (loan_id) id FROM loan_installment
    WHERE status = 'PENDIENTE' ORDER BY loan_id, installment_number
) first_unpaid
WHERE i.id = first_unpaid.id;

ALTER TABLE loan_installment
    ADD CONSTRAINT ck_loan_installment_status CHECK (status IN ('ACTIVA', 'PAGADA', 'PENDIENTE')),
    ADD CONSTRAINT ck_loan_installment_paid_amount CHECK (paid_amount >= 0);

-- Una sola cuota ACTIVA por préstamo
CREATE UNIQUE INDEX IF NOT EXISTS uq_loan_installment_active ON loan_installment (loan_id) WHERE status = 'ACTIVA';

-- ---------- Pagos: liquidación y detalle ----------
ALTER TABLE loan_payment
    ADD COLUMN IF NOT EXISTS payment_date      DATE,
    ADD COLUMN IF NOT EXISTS interest_paid     NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS moratorium        NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS principal         NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS commission        NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS apis              NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS rejection_seen_at TIMESTAMPTZ;

UPDATE loan_payment SET payment_date = (created_at AT TIME ZONE 'America/Lima')::date WHERE payment_date IS NULL;

CREATE TABLE IF NOT EXISTS loan_payment_item (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id         UUID          NOT NULL REFERENCES loan_payment(id) ON DELETE CASCADE,
    installment_id     UUID          REFERENCES loan_installment(id) ON DELETE SET NULL,
    installment_number INTEGER       NOT NULL,
    timing             VARCHAR(10)   NOT NULL CHECK (timing IN ('ANTICIPADA', 'A_TIEMPO', 'CON_ATRASO')),
    used_days          INTEGER       NOT NULL,
    late_days          INTEGER       NOT NULL,
    interest           NUMERIC(12, 2) NOT NULL,
    moratorium         NUMERIC(12, 2) NOT NULL,
    principal          NUMERIC(12, 2) NOT NULL,
    commission         NUMERIC(12, 2) NOT NULL,
    apis               NUMERIC(12, 2) NOT NULL,
    amount             NUMERIC(12, 2) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_loan_payment_item_payment ON loan_payment_item (payment_id);
-- Pagos rechazados sin ver (notificación del cliente)
CREATE INDEX IF NOT EXISTS idx_loan_payment_user_status ON loan_payment (user_id, status);
