package com.join8.identifyr.model.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*

/** ACTIVA = la próxima a pagar (solo una por préstamo) · PAGADA · PENDIENTE = las siguientes. */
enum class InstallmentStatus { ACTIVA, PAGADA, PENDIENTE }

/** Cuota del cronograma de un [Loan]. Monto y vencimiento son inmutables; la cobranza actualiza el pago. */
@Entity
@Table(
    name = "loan_installment",
    uniqueConstraints = [UniqueConstraint(name = "uq_loan_installment_loan_number", columnNames = ["loan_id", "installment_number"])]
)
class LoanInstallment(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    val id: UUID? = null,

    @Column(name = "loan_id", nullable = false, updatable = false)
    val loanId: UUID,

    @Column(name = "installment_number", nullable = false, updatable = false)
    val installmentNumber: Int,

    @Column(name = "due_date", nullable = false)
    var dueDate: LocalDate,

    /** Cuota del contrato. */
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    var amount: BigDecimal,

    // ---- Desglose del cronograma (CreditEngine F14–F18); null en cuotas aún no recalculadas ----
    @Column(name = "t_days")
    var tDays: Int? = null,
    @Column(name = "t_prev_days")
    var tPrevDays: Int? = null,
    @Column(name = "opening_balance", precision = 12, scale = 2)
    var openingBalance: BigDecimal? = null,
    @Column(name = "interest", precision = 12, scale = 2)
    var interest: BigDecimal? = null,
    @Column(name = "principal", precision = 12, scale = 2)
    var principal: BigDecimal? = null,
    @Column(name = "commission", precision = 12, scale = 2)
    var commission: BigDecimal? = null,
    @Column(name = "apis", precision = 12, scale = 2)
    var apis: BigDecimal? = null,
    @Column(name = "closing_balance", precision = 12, scale = 2)
    var closingBalance: BigDecimal? = null,

    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    var paidAmount: BigDecimal = BigDecimal.ZERO.setScale(2),

    @Column(name = "paid_at")
    var paidAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    var status: InstallmentStatus = InstallmentStatus.PENDIENTE,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt
)
