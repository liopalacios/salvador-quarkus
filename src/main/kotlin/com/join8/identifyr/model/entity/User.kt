package com.join8.identifyr.model.entity

import jakarta.persistence.*
import org.hibernate.annotations.GenericGenerator
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "users")
data class User (
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    val id: UUID? = null,

    @Column(name = "dni", length = 8, unique = true, nullable = false)
    var dni: String,

    @Column(name = "full_name", length = 255, nullable = false)
    var fullName: String,

    @Column(name = "phone", length = 9, nullable = false)
    var phone: String,

    @Column(name = "email", length = 255, nullable = false)
    var email: String,

    @Column(name = "address", length = 500)
    var address: String? = null,

    @Column(name = "nationality", length = 100)
    var nationality: String? = null,

    @Column(name = "password_hash", length = 255, nullable = false)
    var passwordHash: String,

    @Column(name = "is_verified")
    var isVerified: Boolean = false,

    @Column(name = "verification_token", length = 255)
    var verificationToken: String? = null,

    @Column(name = "token_expires_at")
    var tokenExpiresAt: LocalDateTime? = null,

    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)