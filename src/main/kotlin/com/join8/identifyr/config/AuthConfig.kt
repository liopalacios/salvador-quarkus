package com.join8.identifyr.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

/** Prefijo "auth" en application.yaml. */
@ConfigurationProperties(prefix = "auth")
data class AuthProperties(
    /** Intentos fallidos consecutivos que bloquean el login del celular. */
    val maxFailedAttempts: Int = 3,
    /** Duración del bloqueo (423). */
    val lockDuration: Duration = Duration.ofMinutes(15),
    /** Tiempo tras POST /users/register en el que se permite POST /auth/register-password. */
    val passwordRegistrationWindow: Duration = Duration.ofMinutes(15)
)

@Configuration
@EnableConfigurationProperties(AuthProperties::class)
class AuthConfig
