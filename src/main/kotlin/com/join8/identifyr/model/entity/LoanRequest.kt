package com.join8.identifyr.model.entity

import jakarta.persistence.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

enum class DepositType { YAPE, PLIN }

enum class LoanCategory { ORO, PLATA, BRONCE }

enum class LoanRequestStatus { PENDIENTE, PROCESADA, RECHAZADA }

/**
 * Solicitud de préstamo. Los datos del solicitante son inmutables (updatable = false):
 * al procesarla o rechazarla solo cambian [status], [operationCode] y [updatedAt].
 */
@Entity
@Table(name = "loan_request")
class LoanRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    val id: UUID? = null,

    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,

    @Column(name = "phone", length = 9, nullable = false, updatable = false)
    val phone: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "deposit_type", length = 4, nullable = false, updatable = false)
    val depositType: DepositType,

    // Foto del score al momento de la solicitud
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 6, nullable = false, updatable = false)
    val category: LoanCategory,
    @Column(name = "score", nullable = false, updatable = false)
    val score: Int,

    @Column(name = "requested_amount", nullable = false, updatable = false)
    val requestedAmount: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    var status: LoanRequestStatus = LoanRequestStatus.PENDIENTE,

    @Column(name = "operation_code", length = 50)
    var operationCode: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt
) {
    companion object {
        // Postgres guarda microsegundos: así lo devuelto coincide con lo guardado
        fun now(): Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)
    }
}
