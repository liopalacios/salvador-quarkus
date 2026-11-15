package com.join8.identifyr.service

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ScheduledInstallment(val number: Int, val dueDate: LocalDate, val amountCents: Long) {
    val amount: BigDecimal get() = LoanScheduleCalculator.cents(amountCents)
}

/** Condiciones y cronograma de un préstamo. Montos en céntimos (enteros); a soles solo al persistir/serializar. */
data class LoanSchedule(
    val plan: LoanPlan,
    val disbursementDate: LocalDate,
    val principalCents: Long,
    val interestCents: Long,
    val totalCents: Long,
    val installments: List<ScheduledInstallment>
) {
    val principal: BigDecimal get() = LoanScheduleCalculator.cents(principalCents)
    val interest: BigDecimal get() = LoanScheduleCalculator.cents(interestCents)
    val total: BigDecimal get() = LoanScheduleCalculator.cents(totalCents)
    val finalDueDate: LocalDate get() = installments.last().dueDate
}

/** Cálculo puro (sin BD ni reloj): mismas reglas que la oferta de la app. */
object LoanScheduleCalculator {

    /** Fecha de desembolso = fecha (en [zone]) del instante en que se registró la operación. */
    fun disbursementDate(operationRegisteredAt: Instant, zone: ZoneId): LocalDate =
        LocalDate.ofInstant(operationRegisteredAt, zone)

    /**
     * - interés = monto * tasa_bp / 100 (céntimos)
     * - cuotas 1..N-1 = floor(total / N); la última se lleva el sobrante
     * - cuota n vence en desembolso + n * frecuencia, salvo la última: desembolso + [LoanPlan.TERM_DAYS]
     */
    fun calculate(amountSoles: Int, plan: LoanPlan, disbursementDate: LocalDate): LoanSchedule {
        require(amountSoles > 0) { "El monto debe ser mayor que 0" }
        val principalCents = amountSoles.toLong() * 100
        val interestCents = amountSoles.toLong() * plan.interestRateBp / 100
        val totalCents = principalCents + interestCents
        val n = plan.installmentCount
        val regularCents = totalCents / n

        val installments = (1..n).map { k ->
            val last = k == n
            ScheduledInstallment(
                number = k,
                dueDate = disbursementDate.plusDays(if (last) LoanPlan.TERM_DAYS.toLong() else k.toLong() * plan.frequencyDays),
                amountCents = if (last) totalCents - regularCents * (n - 1) else regularCents
            )
        }
        return LoanSchedule(plan, disbursementDate, principalCents, interestCents, totalCents, installments)
    }

    fun cents(value: Long): BigDecimal = BigDecimal.valueOf(value, 2)

    /** 1800 -> 18.00 */
    fun basisPointsToPercent(bp: Int): BigDecimal = BigDecimal.valueOf(bp.toLong(), 2)

    /** 1800 -> 0.1800 (columna loan.interest_rate, NUMERIC(5,4)) */
    fun basisPointsToFraction(bp: Int): BigDecimal = BigDecimal.valueOf(bp.toLong(), 4)
}
