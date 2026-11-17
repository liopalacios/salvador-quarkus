package com.join8.identifyr.model.entity

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

/** USER = cliente de la app; SUPERVISOR = procesa solicitudes y desembolsos. */
enum class Role { USER, SUPERVISOR }

@Entity
@Table(name = "users")
data class User (
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    val id: UUID? = null,

    @Column(name = "phone", length = 9, nullable = false)
    var phone: String,

    @Column(name = "email", length = 255, nullable = false)
    var email: String = "",

    /** Id de la foto del anverso del DNI. Null hasta PUT /users/{userId}/dni-photos. */
    @Column(name = "photoFront", length = 255)
    var photoFront: String? = null,

    /** Id de la foto del reverso del DNI. Null hasta PUT /users/{userId}/dni-photos. */
    @Column(name = "photoBack", length = 255)
    var photoBack: String? = null,

    @Column(name = "address", length = 500)
    var address: String? = null,

    @Column(name = "nationality", length = 100)
    var nationality: String? = null,

    /** BCrypt. Null hasta POST /auth/register-password. */
    @Column(name = "password_hash", length = 255)
    var passwordHash: String? = null,

    @Column(name = "is_verified")
    var isVerified: Boolean = false,

    @Column(name = "verification_token", length = 255)
    var verificationToken: String? = null,

    @Column(name = "token_expires_at")
    var tokenExpiresAt: LocalDateTime? = null,

    /** Intentos de login fallidos consecutivos; vuelve a 0 con un login exitoso. */
    @Column(name = "failed_login_attempts", nullable = false)
    var failedLoginAttempts: Int = 0,

    /** Login bloqueado hasta este instante (423). */
    @Column(name = "locked_until")
    var lockedUntil: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    var role: Role = Role.USER,

    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    val hasPassword: Boolean get() = !passwordHash.isNullOrEmpty()

    // Sin passwordHash ni verificationToken: un log del usuario no debe exponerlos
    override fun toString() = "User(id=$id, role=$role)"
}
