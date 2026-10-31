package com.join8.identifyr.model.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * Una versión del cuestionario de un usuario. Las respuestas son inmutables (updatable = false):
 * al desactivar un registro solo cambian [estado] y [updatedAt].
 */
@Entity
@Table(name = "questionnaires")
class Questionnaire(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,

    @Column(name = "employment_type", length = 5, updatable = false)
    val employmentType: String? = null,
    @Column(name = "employment_tenure", length = 5, updatable = false)
    val employmentTenure: String? = null,
    @Column(name = "residential_tenure", length = 5, updatable = false)
    val residentialTenure: String? = null,
    @Column(name = "housing_type", length = 5, updatable = false)
    val housingType: String? = null,
    @Column(name = "dependents", length = 5, updatable = false)
    val dependents: String? = null,

    @Column(name = "email", length = 255, updatable = false)
    val email: String? = null,
    @Column(name = "address", length = 500, updatable = false)
    val address: String? = null,
    @Column(name = "ruc", length = 11, updatable = false)
    val ruc: String? = null,
    @Column(name = "driver_license", length = 50, updatable = false)
    val driverLicense: String? = null,
    @Column(name = "vehicle_plate", length = 20, updatable = false)
    val vehiclePlate: String? = null,
    @Column(name = "education_level", length = 20, updatable = false)
    val educationLevel: String? = null,
    @Column(name = "workplace", length = 255, updatable = false)
    val workplace: String? = null,
    @Column(name = "work_apps", length = 500, updatable = false)
    val workApps: String? = null,
    @Column(name = "account_statement_photo_id", length = 255, updatable = false)
    val accountStatementPhotoId: String? = null,

    // CHAR(1) en la BD (V3); sin JdbcTypeCode Hibernate espera VARCHAR y la validación falla
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "estado", length = 1, nullable = false, columnDefinition = "CHAR(1)")
    var estado: String = ACTIVE,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt
) {
    companion object {
        const val ACTIVE = "A"
        const val INACTIVE = "I"

        // Postgres guarda microsegundos: así lo devuelto coincide con lo guardado
        fun now(): Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)
    }
}
