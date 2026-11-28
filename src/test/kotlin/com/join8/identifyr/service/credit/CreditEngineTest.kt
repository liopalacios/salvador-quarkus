package com.join8.identifyr.service.credit

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Casos de prueba de C:\VUCE\Salvador.xlsx, hoja 5_Casos_prueba (tolerancia S/ 0.00; TCEA ±0.01 puntos).
 * T01–T04: creación del crédito y cronograma · P01–P04: liquidación de pagos.
 */
class CreditEngineTest {

    private val params = CreditParameters(
        teaCap = BigDecimal("1.1468"),
        moraFactor = BigDecimal("0.15"),
        commissionPct = BigDecimal("0.30"),
        apisAmount = BigDecimal("5"),
        baseDays = 360
    )

    // Hoja 7_Feriados
    private val holidays = listOf(
        "2026-11-01", "2026-12-08", "2026-12-09", "2026-12-25", "2027-01-01", "2027-03-25", "2027-03-26",
        "2027-05-01", "2027-06-07", "2027-06-29", "2027-07-23", "2027-07-28", "2027-07-29", "2027-08-06",
        "2027-08-30", "2027-10-08", "2027-11-01", "2027-12-08", "2027-12-09", "2027-12-25"
    ).map(LocalDate::parse).toSet()

    private val nov2 = LocalDate.parse("2026-11-02")

    private fun m(value: String) = BigDecimal(value).setScale(2)
    private fun simulate(amount: Int, modality: Modality, date: LocalDate = nov2) =
        CreditEngine.simulate(BigDecimal(amount), modality, date, params, holidays)

    /** Fila esperada: fecha_vencimiento, t_k, saldo_inicial, interes, capital, comision, apis, cuota, saldo_final. */
    private fun assertRow(row: ScheduleRow, due: String, t: Int, opening: String, interest: String, principal: String,
                          commission: String, apis: String, installment: String, closing: String) {
        val label = "cuota ${row.number}"
        assertEquals(LocalDate.parse(due), row.dueDate, "$label fecha")
        assertEquals(t, row.tDays, "$label t_k")
        assertEquals(m(opening), row.openingBalance, "$label saldo_inicial")
        assertEquals(m(interest), row.interest, "$label interes")
        assertEquals(m(principal), row.principal, "$label capital")
        assertEquals(m(commission), row.commission, "$label comision")
        assertEquals(m(apis), row.apis, "$label apis")
        assertEquals(m(installment), row.installment, "$label cuota")
        assertEquals(m(closing), row.closingBalance, "$label saldo_final")
    }

    private fun assertTcea(expected: Double, actual: BigDecimal) =
        assertTrue(abs(expected * 100 - actual.toDouble() * 100) <= 0.01, "TCEA esperada $expected, obtenida $actual")

    // ---------------- Tasas (F02, F03) ----------------

    @Test
    fun `tasas diarias F02 y F03`() {
        val s = simulate(200, Modality.QUINCENAL)
        assertEquals(0.0021244154945303517, s.ted.toDouble(), 1e-15)
        assertEquals(0.00044101042722721751, s.tedMora.toDouble(), 1e-15)
    }

    // ---------------- A. Creación del crédito ----------------

    @Test
    fun `T01 - 200 quincenal, la cuota 4 cae en feriado y pasa al lunes 04-01`() {
        val s = simulate(200, Modality.QUINCENAL)
        assertEquals(m("70.44"), s.installmentAmount)
        assertEquals(m("16.76"), s.interestTotal)
        assertEquals(m("281.76"), s.totalToPay)
        assertEquals(m("60.00"), s.commissionTotal)
        assertEquals(m("5.00"), s.apisTotal)
        assertTcea(27.617160676661701, s.tcea)
        assertRow(s.schedule[0], "2026-11-17", 15, "200", "6.47", "47.72", "15", "1.25", "70.44", "152.28")
        assertRow(s.schedule[1], "2026-12-02", 30, "152.28", "4.93", "49.26", "15", "1.25", "70.44", "103.02")
        assertRow(s.schedule[2], "2026-12-17", 45, "103.02", "3.33", "50.86", "15", "1.25", "70.44", "52.16")
        assertRow(s.schedule[3], "2027-01-04", 63, "52.16", "2.03", "52.16", "15", "1.25", "70.44", "0")
    }

    @Test
    fun `T02 - 200 mensual, 2da cuota corrida a dia habil`() {
        val s = simulate(200, Modality.MENSUAL)
        assertEquals(m("142.80"), s.installmentAmount)
        assertEquals(m("20.60"), s.interestTotal)
        assertEquals(m("285.60"), s.totalToPay)
        assertTcea(15.8251649129098, s.tcea)
        assertRow(s.schedule[0], "2026-12-02", 30, "200", "13.15", "97.15", "30", "2.5", "142.80", "102.85")
        assertRow(s.schedule[1], "2027-01-04", 63, "102.85", "7.45", "102.85", "30", "2.5", "142.80", "0")
    }

    @Test
    fun `T03 - 500 quincenal, desembolso viernes con vencimientos en sabado y domingo`() {
        val s = simulate(500, Modality.QUINCENAL, LocalDate.parse("2026-12-04"))
        assertEquals(m("174.24"), s.installmentAmount)
        assertEquals(m("41.96"), s.interestTotal)
        assertEquals(m("696.96"), s.totalToPay)
        assertTcea(24.073510935528098, s.tcea)
        assertRow(s.schedule[0], "2026-12-21", 17, "500", "18.37", "117.12", "37.5", "1.25", "174.24", "382.88")
        assertRow(s.schedule[1], "2027-01-04", 31, "382.88", "11.55", "123.94", "37.5", "1.25", "174.24", "258.94")
        assertRow(s.schedule[2], "2027-01-18", 45, "258.94", "7.81", "127.68", "37.5", "1.25", "174.24", "131.26")
        assertRow(s.schedule[3], "2027-02-02", 60, "131.26", "4.23", "131.26", "37.5", "1.25", "174.24", "0")
    }

    @Test
    fun `T04 - 155 quincenal, comision no divisible y la ultima toma el resto`() {
        val s = simulate(155, Modality.QUINCENAL)
        assertEquals(m("54.87"), s.installmentAmount)
        assertEquals(m("12.98"), s.interestTotal)
        assertEquals(m("219.48"), s.totalToPay)
        assertTcea(29.143368459459701, s.tcea)
        assertRow(s.schedule[0], "2026-11-17", 15, "155", "5.01", "36.98", "11.63", "1.25", "54.87", "118.02")
        assertRow(s.schedule[1], "2026-12-02", 30, "118.02", "3.82", "38.17", "11.63", "1.25", "54.87", "79.85")
        assertRow(s.schedule[2], "2026-12-17", 45, "79.85", "2.58", "39.41", "11.63", "1.25", "54.87", "40.44")
        assertRow(s.schedule[3], "2027-01-04", 63, "40.44", "1.57", "40.44", "11.61", "1.25", "54.87", "0")
    }

    // ---------------- C. Liquidación de pagos ----------------

    private fun quote(s: CreditSimulation, k: Int, date: String) =
        CreditEngine.quoteInstallment(s.schedule[k - 1], s.disbursementDate, LocalDate.parse(date), s.ted, s.tedMora)

    private fun assertQuote(q: InstallmentQuote, timing: PaymentTiming, used: Int, late: Int, interest: String, mora: String, amount: String) {
        val label = "cuota ${q.number}"
        assertEquals(timing, q.timing, "$label estado")
        assertEquals(used, q.usedDays, "$label dias_usados")
        assertEquals(late, q.lateDays, "$label dias_atraso")
        assertEquals(m(interest), q.interestToPay, "$label interes_a_pagar")
        assertEquals(m(mora), q.moratorium, "$label interes_moratorio")
        assertEquals(m(amount), q.amountToPay, "$label monto_a_pagar")
    }

    @Test
    fun `P01 - T02 adelantar la cuota 1 al dia 20, la cuota 2 no cambia`() {
        val s = simulate(200, Modality.MENSUAL)
        val q1 = quote(s, 1, "2026-11-22")
        val q2 = quote(s, 2, "2027-01-04")
        assertQuote(q1, PaymentTiming.ANTICIPADA, 20, 0, "8.67", "0", "138.32")
        assertQuote(q2, PaymentTiming.A_TIEMPO, 33, 0, "7.45", "0", "142.80")
        assertEquals(m("281.12"), q1.amountToPay.add(q2.amountToPay))
    }

    @Test
    fun `P02 - T01 cuota 1 a tiempo y cancelar el 100 por ciento el 22-11`() {
        val s = simulate(200, Modality.QUINCENAL)
        val q1 = quote(s, 1, "2026-11-17")
        assertQuote(q1, PaymentTiming.A_TIEMPO, 15, 0, "6.47", "0", "70.44")

        val payoff = CreditEngine.quotePayoff(s.schedule.drop(1), s.disbursementDate, LocalDate.parse("2026-11-22"), s.ted, s.tedMora)
        assertQuote(payoff.items[0], PaymentTiming.ANTICIPADA, 5, 0, "1.62", "0", "67.13")
        assertQuote(payoff.items[1], PaymentTiming.ANTICIPADA, 0, 0, "0", "0", "67.11")
        assertQuote(payoff.items[2], PaymentTiming.ANTICIPADA, 0, 0, "0", "0", "68.41")
        assertEquals(m("202.65"), payoff.total)
        assertEquals(m("273.09"), q1.amountToPay.add(payoff.total))
    }

    @Test
    fun `P03 - T01 cuota 2 pagada 10 dias tarde, interes adicional y moratorio`() {
        val s = simulate(200, Modality.QUINCENAL)
        val quotes = listOf(quote(s, 1, "2026-11-17"), quote(s, 2, "2026-12-12"), quote(s, 3, "2026-12-17"), quote(s, 4, "2027-01-04"))
        assertQuote(quotes[0], PaymentTiming.A_TIEMPO, 15, 0, "6.47", "0", "70.44")
        assertQuote(quotes[1], PaymentTiming.CON_ATRASO, 15, 10, "5.99", "0.22", "71.72")
        assertQuote(quotes[2], PaymentTiming.A_TIEMPO, 15, 0, "3.33", "0", "70.44")
        assertQuote(quotes[3], PaymentTiming.A_TIEMPO, 18, 0, "2.03", "0", "70.44")
        assertEquals(m("283.04"), quotes.fold(BigDecimal.ZERO) { acc, q -> acc.add(q.amountToPay) })
    }

    @Test
    fun `P04 - T01 cancelar el 100 por ciento el dia 5, antes de la cuota 1`() {
        val s = simulate(200, Modality.QUINCENAL)
        val payoff = CreditEngine.quotePayoff(s.schedule, s.disbursementDate, LocalDate.parse("2026-11-07"), s.ted, s.tedMora)
        assertQuote(payoff.items[0], PaymentTiming.ANTICIPADA, 5, 0, "2.13", "0", "66.10")
        assertQuote(payoff.items[1], PaymentTiming.ANTICIPADA, 0, 0, "0", "0", "65.51")
        assertQuote(payoff.items[2], PaymentTiming.ANTICIPADA, 0, 0, "0", "0", "67.11")
        assertQuote(payoff.items[3], PaymentTiming.ANTICIPADA, 0, 0, "0", "0", "68.41")
        assertEquals(m("267.13"), payoff.total)
    }

    // ---------------- Reglas generales ----------------

    @Test
    fun `cuotas iguales, saldos cierran en cero y los totales cuadran`() {
        for (modality in Modality.entries) for (amount in listOf(50, 99, 155, 200, 333, 500, 800)) {
            val s = simulate(amount, modality)
            assertTrue(s.schedule.all { it.installment == s.installmentAmount })
            assertEquals(m("0"), s.schedule.last().closingBalance)
            assertEquals(m(amount.toString()), s.schedule.fold(BigDecimal.ZERO) { a, r -> a.add(r.principal) })
            assertEquals(s.commissionTotal, s.schedule.fold(BigDecimal.ZERO) { a, r -> a.add(r.commission) })
            assertEquals(s.apisTotal, s.schedule.fold(BigDecimal.ZERO) { a, r -> a.add(r.apis) })
            assertEquals(s.totalToPay, s.schedule.fold(BigDecimal.ZERO) { a, r -> a.add(r.installment) })
        }
    }

    @Test
    fun `dia habil salta fines de semana y feriados encadenados`() {
        // 25/12/2026 viernes feriado -> lunes 28
        assertEquals(LocalDate.parse("2026-12-28"), CreditEngine.businessDay(LocalDate.parse("2026-12-25"), holidays))
        // 08/12 y 09/12 feriados -> jueves 10
        assertEquals(LocalDate.parse("2026-12-10"), CreditEngine.businessDay(LocalDate.parse("2026-12-08"), holidays))
        assertEquals(LocalDate.parse("2026-11-17"), CreditEngine.businessDay(LocalDate.parse("2026-11-17"), holidays))
    }
}
