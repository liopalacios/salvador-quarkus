-- ====================================================
-- V3: historial de cuestionarios por estado.
-- Cada envío inserta un registro nuevo; el anterior pasa a estado 'I'.
-- Las respuestas de un registro nunca se modifican.
-- ====================================================
CREATE TABLE IF NOT EXISTS questionnaires (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Bloque 1: respuestas obligatorias (códigos normalizados en minúsculas)
    employment_type VARCHAR(5),
    employment_tenure VARCHAR(5),
    residential_tenure VARCHAR(5),
    housing_type VARCHAR(5),
    dependents VARCHAR(5),

    -- Bloque 2: perfil complementario
    email VARCHAR(255),
    address VARCHAR(500),
    ruc VARCHAR(11),
    driver_license VARCHAR(50),
    vehicle_plate VARCHAR(20),
    education_level VARCHAR(20),
    workplace VARCHAR(255),
    work_apps VARCHAR(500),
    account_statement_photo_id VARCHAR(255),

    estado CHAR(1) NOT NULL CHECK (estado IN ('A', 'I')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_questionnaires_user_id ON questionnaires(user_id);

-- Como máximo un registro activo por usuario
CREATE UNIQUE INDEX uq_questionnaires_user_active ON questionnaires(user_id) WHERE estado = 'A';

CREATE TRIGGER trigger_questionnaires_updated_at BEFORE UPDATE ON questionnaires
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
