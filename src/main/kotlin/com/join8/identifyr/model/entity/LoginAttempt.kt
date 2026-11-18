package com.join8.identifyr.model.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

/**
 * Auditoría de intentos de autenticación (tabla application_attempts de V1). Nunca guarda la contraseña.
 * [failureReason]: UNKNOWN_PHONE, UNKNOWN_USERNAME, DISABLED, BAD_PASSWORD, NO_PASSWORD, LOCKED, LOCKED_NOW, NOT_SUPERVISOR; null si fue exitoso.
 */
@Entity
@Table(name = "application_attempts")
class LoginAttempt(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    val id: UUID? = null,

    @Column(name = "user_id", updatable = false)
    val userId: UUID? = null,

    @Column(name = "phone", length = 9, updatable = false)
    val phone: String?,

    /** Login del personal (STAFF_LOGIN): usuario ingresado y su id en staff_users. */
    @Column(name = "username", length = 50, updatable = false)
    val username: String? = null,

    @Column(name = "staff_user_id", updatable = false)
    val staffUserId: UUID? = null,

    @Column(name = "ip_address", length = 45, updatable = false)
    val ipAddress: String?,

    @Column(name = "attempt_type", length = 50, updatable = false)
    val attemptType: String = TYPE_LOGIN,

    @Column(name = "is_successful", updatable = false)
    val isSuccessful: Boolean,

    @Column(name = "failure_reason", length = 255, updatable = false)
    val failureReason: String? = null,

    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        const val TYPE_LOGIN = "LOGIN"
        const val TYPE_REGISTRATION = "REGISTRATION"
        const val TYPE_STAFF_LOGIN = "STAFF_LOGIN"
    }
}
