-- ====================================================
-- V2: el registro de usuario usa fotos del documento (anverso/reverso)
-- en lugar de DNI y nombre completo.
-- ====================================================
DROP INDEX IF EXISTS idx_users_dni;
ALTER TABLE users DROP COLUMN IF EXISTS dni;
ALTER TABLE users DROP COLUMN IF EXISTS full_name;

-- El DEFAULT '' solo sirve para rellenar filas existentes; luego se quita.
ALTER TABLE users ADD COLUMN photo_front VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN photo_back VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE users ALTER COLUMN photo_front DROP DEFAULT;
ALTER TABLE users ALTER COLUMN photo_back DROP DEFAULT;
