package com.join8.identifyr.service

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Casos obligatorios 1 a 5 del cálculo. Reloj fijo: operación registrada el 2026-11-14 en Lima. */
class LoanScheduleCalculatorTest {

    private val lima = ZoneId.of("America/Lima")
    private val disbursement = LocalDate.parse("2026-11-14")

    private fun money(v: String) = BigDecimal(v)
    private fun dates(vararg d: String) = d.map(LocalDate::parse)

    @Test
    fun `caso 1 - 200 soles quincenal`() {
        val s = LoanScheduleCalculator.calculate(200, LoanPlan.QUINCENAL, disbursement)

        assertEquals(money("200.00"), s.principal)
        assertEquals(money("36.00"), s.interest)
        assertEquals(money("236.00"), s.total)
        assertEquals(List(4) { money("59.00") }, s.installments.map { it.amount })
        assertEquals(listOf(1, 2, 3, 4), s.installments.map { it.number })
        assertEquals(dates("2026-11-29", "2026-12-14", "2026-12-29", "2027-01-14"), s.installments.map { it.dueDate })
    }

    @Test
    fun `caso 2 - 200 soles mensual`() {
        val s = LoanScheduleCalculator.calculate(200, LoanPlan.MENSUAL, disbursement)

        assertEquals(money("42.00"), s.interest)
        assertEquals(money("242.00"), s.total)
        assertEquals(listOf(money("121.00"), money("121.00")), s.installments.map { it.amount })
        assertEquals(dates("2026-12-14", "2027-01-14"), s.installments.map { it.dueDate })
    }

    @Test
    fun `caso 3 - 51 soles quincenal, el sobrante va a la ultima cuota`() {
        val s = LoanScheduleCalculator.calculate(51, LoanPlan.QUINCENAL, disbursement)

        assertEquals(money("9.18"), s.interest)
        assertEquals(money("60.18"), s.total)
        assertEquals(listOf("15.04", "15.04", "15.04", "15.06").map(::money), s.installments.map { it.amount })
        assertEquals(s.totalCents, s.installments.sumOf { it.amountCents })
    }

    @Test
    fun `caso 4 - el cronograma parte de la fecha de desembolso, no de la solicitud`() {
        // La calculadora ni siquiera recibe la fecha de la solicitud (2026-11-01): solo la de desembolso
        val registeredAt = Instant.parse("2026-11-14T15:00:00Z")
        val s = LoanScheduleCalculator.calculate(
            200, LoanPlan.QUINCENAL, LoanScheduleCalculator.disbursementDate(registeredAt, lima)
        )
        assertEquals(LocalDate.parse("2026-11-14"), s.disbursementDate)
        assertEquals(LocalDate.parse("2026-11-29"), s.installments.first().dueDate)
        assertEquals(LocalDate.parse("2027-01-14"), s.finalDueDate)
    }

    @Test
    fun `caso 5 - a las 2330 de Lima (0430 UTC del dia siguiente) la fecha es la de Lima`() {
        val registeredAt = Instant.parse("2026-11-15T04:30:00Z")   // 2026-11-14 23:30 en Lima
        assertEquals(LocalDate.parse("2026-11-14"), LoanScheduleCalculator.disbursementDate(registeredAt, lima))
        // A las 00:00 de Lima ya es el día siguiente
        assertEquals(
            LocalDate.parse("2026-11-15"),
            LoanScheduleCalculator.disbursementDate(Instant.parse("2026-11-15T05:00:00Z"), lima)
        )
    }

    @Test
    fun `la ultima cuota siempre vence a los 61 dias y la suma de cuotas es el total`() {
        for (plan in LoanPlan.entries) for (amount in listOf(1, 7, 51, 99, 200, 1234, 5000)) {
            val s = LoanScheduleCalculator.calculate(amount, plan, disbursement)
            assertEquals(disbursement.plusDays(61), s.finalDueDate, "$plan $amount")
            assertEquals(plan.installmentCount, s.installments.size)
            assertEquals(s.totalCents, s.installments.sumOf { it.amountCents }, "$plan $amount")
            assertEquals(s.installments.sortedBy { it.dueDate }, s.installments, "vencimientos crecientes")
            assertEquals(2, s.total.scale())
        }
    }

    @Test
    fun `planes centralizados`() {
        assertEquals(LoanPlan.QUINCENAL, LoanPlan.forFrequency(15))
        assertEquals(LoanPlan.MENSUAL, LoanPlan.forFrequency(30))
        assertEquals(null, LoanPlan.forFrequency(7))
        assertEquals(null, LoanPlan.forFrequency(null))
        assertEquals(money("18.00"), LoanScheduleCalculator.basisPointsToPercent(LoanPlan.QUINCENAL.interestRateBp))
        assertEquals(money("21.00"), LoanScheduleCalculator.basisPointsToPercent(LoanPlan.MENSUAL.interestRateBp))
        assertFailsWith<IllegalArgumentException> { LoanScheduleCalculator.calculate(0, LoanPlan.QUINCENAL, disbursement) }
    }
}
