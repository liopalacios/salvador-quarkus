-- ====================================================
-- V10: usuarios internos (supervisores) con usuario + contraseña fuerte.
--  * Separados de users (clientes con celular + PIN de 4 dígitos).
--  * POST /auth/staff/login emite un JWT con sub = staff_users.id y role = SUPERVISOR.
--  * Mismo bloqueo por intentos fallidos que users (auth.max-failed-attempts / auth.lock-duration).
--  * Las cuentas se crean por SQL con el hash BCrypt (no hay endpoint de alta).
-- ====================================================
CREATE TABLE IF NOT EXISTS staff_users (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username              VARCHAR(50)  NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    role                  VARCHAR(20)  NOT NULL DEFAULT 'SUPERVISOR' CHECK (role IN ('SUPERVISOR')),
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0),
    locked_until          TIMESTAMPTZ,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- El login no distingue mayúsculas: "Supervisor" y "supervisor" son la misma cuenta
CREATE UNIQUE INDEX IF NOT EXISTS uq_staff_users_username ON staff_users (LOWER(username));

CREATE TRIGGER trigger_staff_users_updated_at
    BEFORE UPDATE ON staff_users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Auditoría: los intentos del personal van a application_attempts con su propio tipo y usuario
-- (user_id referencia a users, por eso el id del supervisor va en staff_user_id).
ALTER TABLE application_attempts
    ADD COLUMN IF NOT EXISTS username VARCHAR(50),
    ADD COLUMN IF NOT EXISTS staff_user_id UUID REFERENCES staff_users(id) ON DELETE SET NULL;

ALTER TABLE application_attempts DROP CONSTRAINT IF EXISTS application_attempts_attempt_type_check;
ALTER TABLE application_attempts ADD CONSTRAINT application_attempts_attempt_type_check
    CHECK (attempt_type IN ('REGISTRATION', 'LOGIN', 'PASSWORD_RESET', 'SCORE_REQUEST', 'STAFF_LOGIN'));

CREATE INDEX IF NOT EXISTS idx_attempts_username_created ON application_attempts (username, created_at DESC);
