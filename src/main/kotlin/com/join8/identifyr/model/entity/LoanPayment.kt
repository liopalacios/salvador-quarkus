package com.join8.identifyr.model.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

/** CUOTA = la próxima cuota sin pagar; TOTAL = todo el saldo pendiente. */
enum class PaymentType { CUOTA, TOTAL }

enum class PaymentStatus { PENDIENTE, CONFIRMADO, RECHAZADO }

/**
 * Pago informado por el cliente con su código de operación. El préstamo y sus cuotas no cambian
 * hasta que el supervisor lo confirma. [amount] se calcula en el servidor al registrarlo.
 */
@Entity
@Table(name = "loan_payment")
class LoanPayment(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    val id: UUID? = null,

    @Column(name = "loan_id", nullable = false, updatable = false)
    val loanId: UUID,

    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", length = 6, nullable = false, updatable = false)
    val type: PaymentType,

    /** Solo en CUOTA: la cuota que se paga. */
    @Column(name = "installment_id", updatable = false)
    val installmentId: UUID? = null,

    @Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
    val amount: BigDecimal,

    @Column(name = "operation_code", length = 50, nullable = false, updatable = false)
    val operationCode: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    var status: PaymentStatus = PaymentStatus.PENDIENTE,

    @Column(name = "rejection_reason", length = 255)
    var rejectionReason: String? = null,

    /** Fecha (Lima) con la que el motor liquidó el pago: el día en que el cliente lo informó. */
    @Column(name = "payment_date")
    val paymentDate: java.time.LocalDate? = null,

    // Desglose del monto (suma de loan_payment_item)
    @Column(name = "interest_paid", precision = 12, scale = 2)
    val interest: BigDecimal? = null,
    @Column(name = "moratorium", precision = 12, scale = 2)
    val moratorium: BigDecimal? = null,
    @Column(name = "principal", precision = 12, scale = 2)
    val principal: BigDecimal? = null,
    @Column(name = "commission", precision = 12, scale = 2)
    val commission: BigDecimal? = null,
    @Column(name = "apis", precision = 12, scale = 2)
    val apis: BigDecimal? = null,

    /** El cliente ya vio la notificación del rechazo. */
    @Column(name = "rejection_seen_at")
    var rejectionSeenAt: Instant? = null,

    /** "sub" del token del supervisor que lo confirmó o rechazó. */
    @Column(name = "reviewed_by", length = 100)
    var reviewedBy: String? = null,

    @Column(name = "reviewed_at")
    var reviewedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt
) {
    companion object {
        fun now(): Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)
    }
}
