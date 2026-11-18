package com.join8.identifyr.model.entity

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

/**
 * Personal interno (hoy solo SUPERVISOR) con usuario + contraseña fuerte (BCrypt).
 * Separado de [User]: los clientes entran con celular + PIN de 4 dígitos.
 */
@Entity
@Table(name = "staff_users")
class StaffUser(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    val id: UUID? = null,

    @Column(name = "username", length = 50, nullable = false)
    var username: String,

    @Column(name = "password_hash", length = 255, nullable = false)
    var passwordHash: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    var role: Role = Role.SUPERVISOR,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,

    /** Intentos de login fallidos consecutivos; vuelve a 0 con un login exitoso. */
    @Column(name = "failed_login_attempts", nullable = false)
    var failedLoginAttempts: Int = 0,

    /** Login bloqueado hasta este instante (423). */
    @Column(name = "locked_until")
    var lockedUntil: Instant? = null,

    @Column(name = "created_at", updatable = false, nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    // Sin passwordHash: un log del usuario no debe exponerlo
    override fun toString() = "StaffUser(id=$id, role=$role)"
}
