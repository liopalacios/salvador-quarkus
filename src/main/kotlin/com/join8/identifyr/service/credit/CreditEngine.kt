package com.join8.identifyr.service.credit

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow

/**
 * Motor de cálculo de créditos de Salvador. Fuente de verdad: C:\VUCE\Salvador.xlsx (v3.0), hojas 2_Formulas
 * (F01–F28) y 5_Casos_prueba. Cálculo puro: sin base de datos ni reloj; todo en BigDecimal.
 *  - Tasas con todos sus decimales (MathContext de 34 dígitos); montos con ROUND2 = 2 decimales HALF_UP.
 *  - Días calendario desde la fecha de desembolso (día 0); año base [CreditParameters.baseDays].
 */
object CreditEngine {

    private val MC = MathContext(34, RoundingMode.HALF_EVEN)
    private val ZERO2: BigDecimal = BigDecimal.ZERO.setScale(2)

    fun round2(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)

    // ---------------- Creación del crédito (F01–F20) ----------------

    fun simulate(
        amount: BigDecimal,
        modality: Modality,
        disbursementDate: LocalDate,
        params: CreditParameters,
        holidays: Set<LocalDate>
    ): CreditSimulation {
        require(amount.signum() > 0) { "El monto debe ser mayor que 0" }
        val monto = amount.setScale(2, RoundingMode.HALF_UP)
        val n = modality.installments

        val tea = params.teaCap                                                        // F01
        val ted = dailyRate(tea, params.baseDays)                                      // F02
        val tedMora = dailyRate(params.teaCap.multiply(params.moraFactor, MC), params.baseDays) // F03

        val dueDates = (1..n).map { k ->
            businessDay(disbursementDate.plusDays(k.toLong() * modality.periodDays), holidays) // F06, F07
        }
        val t = dueDates.map { ChronoUnit.DAYS.between(disbursementDate, it).toInt() }   // F08
        val onePlusTed = BigDecimal.ONE.add(ted, MC)
        val factorSum = t.fold(BigDecimal.ZERO) { acc, tk -> acc.add(BigDecimal.ONE.divide(onePlusTed.pow(tk, MC), MC), MC) } // F09
        val cuotaBase = monto.divide(factorSum, MC)                                    // F10
        val comisionTotal = round2(monto.multiply(params.commissionPct, MC))           // F11
        val apisTotal = params.apisAmount.setScale(2, RoundingMode.HALF_UP)           // F12
        val nBig = BigDecimal(n)
        val cuota = round2(cuotaBase.add(comisionTotal.divide(nBig, MC), MC).add(apisTotal.divide(nBig, MC), MC)) // F13

        // F14–F18: cronograma en orden k = 1..n
        val rows = ArrayList<ScheduleRow>(n)
        var saldo = monto
        var comisionAcum = ZERO2
        var apisAcum = ZERO2
        for (k in 1..n) {
            val last = k == n
            val tPrev = if (k == 1) 0 else t[k - 2]
            val comision = if (last) comisionTotal.subtract(comisionAcum) else round2(comisionTotal.divide(nBig, MC))
            val apis = if (last) apisTotal.subtract(apisAcum) else round2(apisTotal.divide(nBig, MC))
            val interes: BigDecimal
            val capital: BigDecimal
            if (last) {
                capital = saldo
                interes = cuota.subtract(capital).subtract(comision).subtract(apis)
            } else {
                interes = round2(saldo.multiply(growth(onePlusTed, t[k - 1] - tPrev), MC))
                capital = cuota.subtract(interes).subtract(comision).subtract(apis)
            }
            val saldoFinal = saldo.subtract(capital)
            rows += ScheduleRow(
                number = k,
                nominalDate = disbursementDate.plusDays(k.toLong() * modality.periodDays),
                dueDate = dueDates[k - 1],
                tDays = t[k - 1],
                tPrevDays = tPrev,
                openingBalance = saldo,
                interest = interes,
                principal = capital,
                commission = comision,
                apis = apis,
                installment = cuota,
                closingBalance = saldoFinal
            )
            saldo = saldoFinal
            comisionAcum = comisionAcum.add(comision)
            apisAcum = apisAcum.add(apis)
        }

        val interesTotal = rows.fold(ZERO2) { acc, r -> acc.add(r.interest) }
        val totalAPagar = cuota.multiply(nBig)                                         // F19
        return CreditSimulation(
            amount = monto,
            modality = modality,
            disbursementDate = disbursementDate,
            tea = tea,
            ted = ted,
            tedMora = tedMora,
            commissionTotal = comisionTotal,
            apisTotal = apisTotal,
            installmentAmount = cuota,
            interestTotal = interesTotal,
            totalToPay = totalAPagar,
            tcea = tcea(monto, cuota, t, params.baseDays),                             // F20
            schedule = rows
        )
    }

    /** F02/F03: (1 + tasaAnual)^(1/base) − 1, con todos los decimales. */
    fun dailyRate(annualRate: BigDecimal, baseDays: Int): BigDecimal =
        nthRoot(BigDecimal.ONE.add(annualRate, MC), baseDays).subtract(BigDecimal.ONE, MC)

    /** F07: si cae sábado, domingo o feriado, pasa al siguiente día hábil. */
    fun businessDay(date: LocalDate, holidays: Set<LocalDate>): LocalDate {
        var d = date
        while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY || d in holidays) d = d.plusDays(1)
        return d
    }

    /**
     * F20: tasa diaria r tal que monto = Σ cuota / (1 + r)^t_k (Newton-Raphson; bisección si no converge),
     * TCEA = (1 + r)^base − 1. Se usa double: es un indicador para mostrar (2 decimales en %).
     */
    fun tcea(amount: BigDecimal, installment: BigDecimal, t: List<Int>, baseDays: Int): BigDecimal {
        val m = amount.toDouble()
        val c = installment.toDouble()
        fun f(r: Double) = t.sumOf { c / (1 + r).pow(it) } - m
        fun df(r: Double) = t.sumOf { -it * c / (1 + r).pow(it + 1) }

        var r = 0.01
        var converged = false
        repeat(100) {
            val next = r - f(r) / df(r)
            if (next.isFinite() && next > 0) {
                if (kotlin.math.abs(next - r) < 1e-15) { r = next; converged = true; return@repeat }
                r = next
            }
        }
        if (!converged || kotlin.math.abs(f(r)) > 1e-9) {
            var lo = 1e-9
            var hi = 1.0
            repeat(200) {
                val mid = (lo + hi) / 2
                if (f(mid) > 0) lo = mid else hi = mid
            }
            r = (lo + hi) / 2
        }
        return BigDecimal((1 + r).pow(baseDays) - 1, MathContext(17))
    }

    // ---------------- Liquidación de pagos (F21–F28) ----------------

    /**
     * F21–F27: cuánto se paga la cuota [row] si se paga en [paymentDate].
     * ANTICIPADA: interés solo por los días usados del periodo · A_TIEMPO: el del cronograma ·
     * CON_ATRASO: interés del cronograma + interés adicional y moratorio sobre el capital de la cuota.
     * Comisión y APIs no se reducen por pagar antes.
     */
    fun quoteInstallment(
        row: ScheduleRow,
        disbursementDate: LocalDate,
        paymentDate: LocalDate,
        ted: BigDecimal,
        tedMora: BigDecimal
    ): InstallmentQuote {
        val p = ChronoUnit.DAYS.between(disbursementDate, paymentDate).toInt()        // F21
        val status = when {                                                            // F22
            p < row.tDays -> PaymentTiming.ANTICIPADA
            p == row.tDays -> PaymentTiming.A_TIEMPO
            else -> PaymentTiming.CON_ATRASO
        }
        val usedDays = maxOf(0, minOf(p, row.tDays) - row.tPrevDays)                   // F23
        val lateDays = maxOf(0, p - row.tDays)                                         // F25
        val onePlusTed = BigDecimal.ONE.add(ted, MC)
        val interestToPay = when (status) {                                            // F24
            PaymentTiming.ANTICIPADA -> round2(row.openingBalance.multiply(growth(onePlusTed, usedDays), MC))
            PaymentTiming.A_TIEMPO -> row.interest
            PaymentTiming.CON_ATRASO -> row.interest.add(round2(row.principal.multiply(growth(onePlusTed, lateDays), MC)))
        }
        val moratorium = round2(row.principal.multiply(growth(BigDecimal.ONE.add(tedMora, MC), lateDays), MC)) // F26
        val total = row.principal.add(interestToPay).add(moratorium).add(row.commission).add(row.apis)          // F27
        return InstallmentQuote(
            number = row.number,
            paymentDate = paymentDate,
            timing = status,
            usedDays = usedDays,
            lateDays = lateDays,
            interestToPay = interestToPay,
            moratorium = moratorium,
            principal = row.principal,
            commission = row.commission,
            apis = row.apis,
            amountToPay = total,
            contractInstallment = row.installment
        )
    }

    /** F28: cancelar el 100% en [paymentDate] = Σ F27 de las cuotas no pagadas [unpaid]. */
    fun quotePayoff(
        unpaid: List<ScheduleRow>,
        disbursementDate: LocalDate,
        paymentDate: LocalDate,
        ted: BigDecimal,
        tedMora: BigDecimal
    ): PayoffQuote {
        val items = unpaid.sortedBy { it.number }.map { quoteInstallment(it, disbursementDate, paymentDate, ted, tedMora) }
        return PayoffQuote(paymentDate, items, items.fold(ZERO2) { acc, q -> acc.add(q.amountToPay) })
    }

    // ---------------- Aritmética ----------------

    // (1 + r)^días − 1 con días enteros ≥ 0
    private fun growth(onePlusRate: BigDecimal, days: Int): BigDecimal =
        if (days <= 0) BigDecimal.ZERO else onePlusRate.pow(days, MC).subtract(BigDecimal.ONE, MC)

    // Raíz n-ésima por Newton en BigDecimal, partiendo de la aproximación en double
    private fun nthRoot(x: BigDecimal, n: Int): BigDecimal {
        val nBig = BigDecimal(n)
        var y = BigDecimal(x.toDouble().pow(1.0 / n), MC)
        repeat(50) {
            val next = y.multiply(BigDecimal(n - 1), MC).add(x.divide(y.pow(n - 1, MC), MC), MC).divide(nBig, MC)
            if (next.compareTo(y) == 0) return next
            y = next
        }
        return y
    }
}

enum class Modality(val periodDays: Int, val installments: Int) {
    QUINCENAL(15, 4),
    MENSUAL(30, 2);

    companion object {
        fun forPeriod(days: Int?): Modality? = entries.firstOrNull { it.periodDays == days }
        val allowedPeriods: List<Int> get() = entries.map { it.periodDays }
    }
}

/** Parámetros vigentes (hoja 1_Parametros). Se guardan como snapshot en cada préstamo. */
data class CreditParameters(
    val teaCap: BigDecimal,
    val moraFactor: BigDecimal,
    val commissionPct: BigDecimal,
    val apisAmount: BigDecimal,
    val baseDays: Int
)

data class ScheduleRow(
    val number: Int,
    val nominalDate: LocalDate,
    val dueDate: LocalDate,
    val tDays: Int,
    val tPrevDays: Int,
    val openingBalance: BigDecimal,
    val interest: BigDecimal,
    val principal: BigDecimal,
    val commission: BigDecimal,
    val apis: BigDecimal,
    val installment: BigDecimal,
    val closingBalance: BigDecimal
)

data class CreditSimulation(
    val amount: BigDecimal,
    val modality: Modality,
    val disbursementDate: LocalDate,
    val tea: BigDecimal,
    val ted: BigDecimal,
    val tedMora: BigDecimal,
    val commissionTotal: BigDecimal,
    val apisTotal: BigDecimal,
    val installmentAmount: BigDecimal,
    val interestTotal: BigDecimal,
    val totalToPay: BigDecimal,
    /** Fracción: 27.6171… = 2,761.72% */
    val tcea: BigDecimal,
    val schedule: List<ScheduleRow>
) {
    val finalDueDate: LocalDate get() = schedule.last().dueDate
    val termDays: Int get() = schedule.last().tDays
}

enum class PaymentTiming { ANTICIPADA, A_TIEMPO, CON_ATRASO }

data class InstallmentQuote(
    val number: Int,
    val paymentDate: LocalDate,
    val timing: PaymentTiming,
    val usedDays: Int,
    val lateDays: Int,
    val interestToPay: BigDecimal,
    val moratorium: BigDecimal,
    val principal: BigDecimal,
    val commission: BigDecimal,
    val apis: BigDecimal,
    val amountToPay: BigDecimal,
    val contractInstallment: BigDecimal
) {
    /** Negativo = ahorro por pagar antes; positivo = recargo por atraso. */
    val difference: BigDecimal get() = amountToPay.subtract(contractInstallment)
}

data class PayoffQuote(val paymentDate: LocalDate, val items: List<InstallmentQuote>, val total: BigDecimal)
