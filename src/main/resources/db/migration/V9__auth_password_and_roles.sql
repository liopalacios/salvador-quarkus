-- ====================================================
-- V9: autenticación con contraseña de 4 dígitos + JWT.
--  * Las fotos del DNI ya no llegan en POST /users/register: columnas NULLABLE
--    (se completan luego con PUT /users/{userId}/dni-photos).
--  * password_hash NULLABLE hasta que el usuario registre su contraseña.
--  * Bloqueo de login por celular: failed_login_attempts / locked_until.
--  * role: USER (cliente) o SUPERVISOR.
-- Los '' que guardaba el registro anterior pasan a NULL (deshabilitando el trigger
-- para no pisar updated_at con la fecha de esta migración).
-- ====================================================
ALTER TABLE users ALTER COLUMN photo_front DROP NOT NULL;
ALTER TABLE users ALTER COLUMN photo_back DROP NOT NULL;
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE users DISABLE TRIGGER trigger_users_updated_at;
UPDATE users SET photo_front = NULL WHERE photo_front = '';
UPDATE users SET photo_back = NULL WHERE photo_back = '';
UPDATE users SET password_hash = NULL WHERE password_hash = '';
ALTER TABLE users ENABLE TRIGGER trigger_users_updated_at;

ALTER TABLE users
    ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0),
    ADD COLUMN locked_until TIMESTAMPTZ,
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'SUPERVISOR'));

-- El login es por celular: un celular debe identificar a un solo usuario.
-- Solo se crea si hoy no hay duplicados (si los hay, la migración no falla: depurarlos y crear el índice a mano).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM users GROUP BY phone HAVING COUNT(*) > 1) THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uq_users_phone ON users(phone);
    ELSE
        RAISE WARNING 'users.phone tiene duplicados: no se creó uq_users_phone';
    END IF;
END $$;

-- Auditoría de login (tabla de V1): búsquedas por celular y fecha
CREATE INDEX IF NOT EXISTS idx_attempts_phone_created ON application_attempts(phone, created_at DESC);
