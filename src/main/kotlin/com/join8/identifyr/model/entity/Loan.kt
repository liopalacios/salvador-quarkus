package com.join8.identifyr.model.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*

/** VIGENTE equivale al estado "ACTIVO" del préstamo. */
enum class LoanStatus { VIGENTE, PAGADO, VENCIDO }

/**
 * Préstamo desembolsado a partir de una [LoanRequest]. Las condiciones son inmutables
 * (updatable = false): más adelante solo cambian [status], [outstandingBalance] y [updatedAt].
 * Montos en soles con 2 decimales; nunca float/double.
 */
@Entity
@Table(name = "loan")
class Loan(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    val id: UUID? = null,

    @Column(name = "loan_request_id", nullable = false, updatable = false, unique = true)
    val loanRequestId: UUID,

    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,

    @Column(name = "phone", length = 9, nullable = false, updatable = false)
    val phone: String,

    @Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
    val amount: BigDecimal,
    @Column(name = "interest", nullable = false, updatable = false, precision = 12, scale = 2)
    val interest: BigDecimal,
    /** Fracción: 0.1800 = 18%. */
    @Column(name = "interest_rate", nullable = false, updatable = false, precision = 5, scale = 4)
    val interestRate: BigDecimal,
    @Column(name = "total_amount", nullable = false, updatable = false, precision = 12, scale = 2)
    val totalAmount: BigDecimal,

    @Column(name = "installment_frequency", nullable = false, updatable = false)
    val installmentFrequency: Int,
    @Column(name = "installment_count", nullable = false, updatable = false)
    val installmentCount: Int,
    @Column(name = "term_days", nullable = false, updatable = false)
    val termDays: Int,

    @Column(name = "operation_code", length = 50, nullable = false, updatable = false, unique = true)
    val operationCode: String,

    @Column(name = "score", nullable = false, updatable = false)
    val score: Int,
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 6, nullable = false, updatable = false)
    val category: LoanCategory,
    @Enumerated(EnumType.STRING)
    @Column(name = "deposit_type", length = 4, nullable = false, updatable = false)
    val depositType: DepositType,

    /** Hora del servidor en que se registró el código de operación. Base de todo el cronograma. */
    @Column(name = "operation_registered_at", nullable = false, updatable = false)
    val operationRegisteredAt: Instant,
    /** Fecha (America/Lima) de [operationRegisteredAt]. */
    @Column(name = "disbursement_date", nullable = false, updatable = false)
    val disbursementDate: LocalDate,
    /** Copia histórica de loan_request.created_at; no interviene en el cálculo. */
    @Column(name = "request_date", nullable = false, updatable = false)
    val requestDate: Instant,

    /** Vencimiento de la última cuota. */
    @Column(name = "payment_date", nullable = false, updatable = false)
    val paymentDate: LocalDate,
    /** Plazo que muestra la app (requisito de Google Play); hoy igual a [paymentDate]. */
    @Column(name = "app_payment_date", nullable = false, updatable = false)
    val appPaymentDate: LocalDate,

    @Column(name = "outstanding_balance", nullable = false, precision = 12, scale = 2)
    var outstandingBalance: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    var status: LoanStatus = LoanStatus.VIGENTE,

    @Column(name = "created_by", length = 100, updatable = false)
    val createdBy: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt
)
