package com.join8.identifyr.model.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*

enum class LoanStatus { VIGENTE, PAGADO, VENCIDO }

/**
 * Préstamo desembolsado a partir de una [LoanRequest]. Las condiciones son inmutables
 * (updatable = false): más adelante solo cambian [status] y [updatedAt].
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

    @Column(name = "amount", nullable = false, updatable = false)
    val amount: Int,
    @Column(name = "interest", nullable = false, updatable = false)
    val interest: Int,
    @Column(name = "interest_rate", nullable = false, updatable = false, precision = 5, scale = 4)
    val interestRate: BigDecimal,
    @Column(name = "total_amount", nullable = false, updatable = false)
    val totalAmount: Int,

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

    @Column(name = "payment_date", nullable = false, updatable = false)
    val paymentDate: LocalDate,
    @Column(name = "app_payment_date", nullable = false, updatable = false)
    val appPaymentDate: LocalDate,

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
