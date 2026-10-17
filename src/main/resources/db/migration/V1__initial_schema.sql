-- =============================================
-- JOIN8 - MIGRACIÓN INICIAL
-- Capas 0, 1 y 2
-- =============================================

-- 1. TABLA USERS (Capa 0 - Identity / Antifraude)
-- =============================================
CREATE TABLE IF NOT EXISTS users (
                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dni VARCHAR(8) UNIQUE NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    phone VARCHAR(9) NOT NULL,
    email VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    nationality VARCHAR(100),
    password_hash VARCHAR(255) NOT NULL,
    is_verified BOOLEAN DEFAULT FALSE,
    verification_token VARCHAR(255),
    token_expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX idx_users_dni ON users(dni);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_users_created_at ON users(created_at);

-- 2. TABLA USER_CONTEXT (Capa 1 - Identity & Context)
-- ====================================================
CREATE TABLE IF NOT EXISTS user_context (
                                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    birth_date DATE NOT NULL,
    address TEXT,
    district VARCHAR(100),
    housing_type VARCHAR(50) CHECK (housing_type IN ('OWNED', 'RENTED', 'FAMILY', 'ASSIGNED', 'OTHER')),
    residential_stability_months INTEGER CHECK (residential_stability_months >= 0),
    education_level VARCHAR(50) CHECK (education_level IN ('PRIMARY', 'SECONDARY', 'TECHNICAL', 'UNIVERSITY', 'POSTGRADUATE', 'NONE')),
    dependents_count INTEGER DEFAULT 0 CHECK (dependents_count >= 0),
    economic_activity VARCHAR(100),
    economic_activity_type VARCHAR(50) CHECK (economic_activity_type IN ('COMMERCE', 'DELIVERY', 'TAXI', 'INDEPENDENT', 'EMPLOYED', 'UNEMPLOYED', 'OTHER')),
    economic_activity_tenure_months INTEGER CHECK (economic_activity_tenure_months >= 0),
    nationality_segment VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_context_user FOREIGN KEY (user_id) REFERENCES users(id)
    );

CREATE INDEX idx_context_user_id ON user_context(user_id);
CREATE INDEX idx_context_district ON user_context(district);
CREATE INDEX idx_context_housing_type ON user_context(housing_type);
CREATE INDEX idx_context_activity_type ON user_context(economic_activity_type);

-- 3. TABLA DIGITAL_STABILITY (Capa 2 - Digital Stability)
-- ========================================================
CREATE TABLE IF NOT EXISTS digital_stability (
                                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    phone_age_months INTEGER CHECK (phone_age_months >= 0),
    email_age_months INTEGER CHECK (email_age_months >= 0),
    device_tenure_months INTEGER CHECK (device_tenure_months >= 0),
    device_consistency_count INTEGER DEFAULT 1 CHECK (device_consistency_count >= 0),
    sim_changes_12m INTEGER DEFAULT 0 CHECK (sim_changes_12m >= 0),
    device_changes_30d INTEGER DEFAULT 0 CHECK (device_changes_30d >= 0),
    home_zone_stability_30d INTEGER DEFAULT 0 CHECK (home_zone_stability_30d >= 0),
    mobility_radius_30d INTEGER DEFAULT 0 CHECK (mobility_radius_30d >= 0),
    last_device_id VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stability_user FOREIGN KEY (user_id) REFERENCES users(id)
    );

CREATE INDEX idx_stability_user_id ON digital_stability(user_id);
CREATE INDEX idx_stability_phone_age ON digital_stability(phone_age_months);
CREATE INDEX idx_stability_email_age ON digital_stability(email_age_months);

-- 4. TABLA SCORES (Resultados del Scoring)
-- =========================================
CREATE TABLE IF NOT EXISTS scores (
                                      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    score_value INTEGER NOT NULL CHECK (score_value BETWEEN 0 AND 100),
    score_version VARCHAR(20) DEFAULT 'V0',
    score_type VARCHAR(50) DEFAULT 'INTEGRITY',
    breakdown JSONB,  -- Desglose por capa: {"capa0": 25, "capa1": 35, "capa2": 20}
    calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    request_id VARCHAR(100),
    CONSTRAINT fk_score_user FOREIGN KEY (user_id) REFERENCES users(id)
    );

CREATE INDEX idx_scores_user_id ON scores(user_id);
CREATE INDEX idx_scores_calculated_at ON scores(calculated_at);
CREATE INDEX idx_scores_value ON scores(score_value);

-- 5. TABLA USER_SESSIONS (Para control de sesiones JWT)
-- ======================================================
CREATE TABLE IF NOT EXISTS user_sessions (
                                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id VARCHAR(255),
    device_model VARCHAR(255),
    os_version VARCHAR(50),
    app_version VARCHAR(20),
    refresh_token VARCHAR(500),
    token_expires_at TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users(id)
    );

CREATE INDEX idx_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_sessions_device_id ON user_sessions(device_id);
CREATE INDEX idx_sessions_active ON user_sessions(is_active);

-- 6. TABLA APPLICATION_ATTEMPTS (Para control de velocidad / antifraude)
-- ======================================================================
CREATE TABLE IF NOT EXISTS application_attempts (
                                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    dni VARCHAR(8),
    email VARCHAR(255),
    phone VARCHAR(9),
    ip_address VARCHAR(45),
    device_id VARCHAR(255),
    attempt_type VARCHAR(50) CHECK (attempt_type IN ('REGISTRATION', 'LOGIN', 'PASSWORD_RESET', 'SCORE_REQUEST')),
    is_successful BOOLEAN DEFAULT FALSE,
    failure_reason VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX idx_attempts_user_id ON application_attempts(user_id);
CREATE INDEX idx_attempts_dni ON application_attempts(dni);
CREATE INDEX idx_attempts_ip ON application_attempts(ip_address);
CREATE INDEX idx_attempts_device ON application_attempts(device_id);
CREATE INDEX idx_attempts_created_at ON application_attempts(created_at);

-- =============================================
-- AUDITORÍA (Triggers automáticos)
-- =============================================

-- Función para actualizar updated_at automáticamente
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Triggers para cada tabla
CREATE TRIGGER trigger_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trigger_user_context_updated_at BEFORE UPDATE ON user_context FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trigger_digital_stability_updated_at BEFORE UPDATE ON digital_stability FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trigger_user_sessions_updated_at BEFORE UPDATE ON user_sessions FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =============================================
-- DATOS INICIALES (Opcionales)
-- =============================================

-- Índices adicionales para búsquedas frecuentes
CREATE INDEX idx_scores_user_value ON scores(user_id, score_value);
CREATE INDEX idx_attempts_dni_created ON application_attempts(dni, created_at DESC);