package com.join8.identifyr.model.entity

import com.join8.identifyr.service.credit.PaymentTiming
import jakarta.persistence.*
import java.math.BigDecimal
import java.util.*

/** Liquidación de una cuota dentro de un [LoanPayment] (CreditEngine F21–F27). */
@Entity
@Table(name = "loan_payment_item")
class LoanPaymentItem(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    val id: UUID? = null,

    @Column(name = "payment_id", nullable = false, updatable = false)
    val paymentId: UUID,

    @Column(name = "installment_id", updatable = false)
    val installmentId: UUID?,

    @Column(name = "installment_number", nullable = false, updatable = false)
    val installmentNumber: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "timing", length = 10, nullable = false, updatable = false)
    val timing: PaymentTiming,

    @Column(name = "used_days", nullable = false, updatable = false)
    val usedDays: Int,
    @Column(name = "late_days", nullable = false, updatable = false)
    val lateDays: Int,

    @Column(name = "interest", nullable = false, updatable = false, precision = 12, scale = 2)
    val interest: BigDecimal,
    @Column(name = "moratorium", nullable = false, updatable = false, precision = 12, scale = 2)
    val moratorium: BigDecimal,
    @Column(name = "principal", nullable = false, updatable = false, precision = 12, scale = 2)
    val principal: BigDecimal,
    @Column(name = "commission", nullable = false, updatable = false, precision = 12, scale = 2)
    val commission: BigDecimal,
    @Column(name = "apis", nullable = false, updatable = false, precision = 12, scale = 2)
    val apis: BigDecimal,
    @Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
    val amount: BigDecimal
)
