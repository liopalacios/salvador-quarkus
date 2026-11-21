-- ====================================================
-- V11: imágenes del DNI (anverso y reverso) guardadas en Postgres.
--  * POST /users/{userId}/dni-photos (multipart) las guarda y escribe sus ids en users.photo_front / photo_back.
--  * Una foto por lado y usuario: subirlas de nuevo reemplaza las anteriores.
--  * Solo JPEG o PNG, máximo 5 MB cada una (lo valida el backend).
-- ====================================================
CREATE TABLE IF NOT EXISTS dni_photos (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    side         VARCHAR(10) NOT NULL CHECK (side IN ('FRONT', 'BACK')),
    content_type VARCHAR(20) NOT NULL,
    size_bytes   INTEGER     NOT NULL CHECK (size_bytes > 0),
    content      BYTEA       NOT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_dni_photos_user_side ON dni_photos (user_id, side);
