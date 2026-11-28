package com.join8.identifyr.service.credit

import com.join8.identifyr.model.dto.response.InstallmentQuoteResponse
import com.join8.identifyr.model.dto.response.LoanInstallmentResponse
import com.join8.identifyr.model.dto.response.PaymentQuoteResponse
import com.join8.identifyr.model.dto.response.PayoffQuoteResponse
import com.join8.identifyr.model.entity.InstallmentStatus
import com.join8.identifyr.model.entity.LoanInstallment
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Mapeo de resultados del motor a las respuestas de la API. */
object CreditResponses {

    private val ZERO2: BigDecimal = BigDecimal.ZERO.setScale(2)

    /** Fracción -> porcentaje con 2 decimales: 1.1468 -> 114.68 */
    fun percent(fraction: BigDecimal): BigDecimal = fraction.movePointRight(2).setScale(2, RoundingMode.HALF_UP)

    fun installment(i: LoanInstallment) = LoanInstallmentResponse(
        numeroCuota = i.installmentNumber,
        fechaVencimiento = i.dueDate,
        montoCuota = i.amount,
        estado = i.status.name,
        saldoInicial = i.openingBalance,
        interes = i.interest,
        capital = i.principal,
        comision = i.commission,
        apis = i.apis,
        saldoFinal = i.closingBalance,
        montoPagado = if (i.status == InstallmentStatus.PAGADA) i.paidAmount else null,
        fechaPago = if (i.status == InstallmentStatus.PAGADA) i.paidAt else null
    )

    fun quote(q: InstallmentQuote) = InstallmentQuoteResponse(
        numeroCuota = q.number,
        estadoPago = q.timing.name,
        diasUsados = q.usedDays,
        diasAtraso = q.lateDays,
        capital = q.principal,
        interes = q.interestToPay,
        interesMoratorio = q.moratorium,
        comision = q.commission,
        apis = q.apis,
        montoAPagar = q.amountToPay,
        cuotaContrato = q.contractInstallment,
        diferencia = q.difference
    )

    fun payoff(p: PayoffQuote): PayoffQuoteResponse {
        fun sum(f: (InstallmentQuote) -> BigDecimal) = p.items.fold(ZERO2) { acc, q -> acc.add(f(q)) }
        val contract = sum { it.contractInstallment }
        return PayoffQuoteResponse(
            monto = p.total,
            capital = sum { it.principal },
            interes = sum { it.interestToPay },
            interesMoratorio = sum { it.moratorium },
            comision = sum { it.commission },
            apis = sum { it.apis },
            totalContrato = contract,
            diferencia = p.total.subtract(contract),
            cuotas = p.items.map(::quote)
        )
    }

    fun paymentQuote(date: LocalDate, active: InstallmentQuote?, payoff: PayoffQuote?): PaymentQuoteResponse? =
        if (active == null && payoff == null) null
        else PaymentQuoteResponse(fechaPago = date, cuota = active?.let(::quote), total = payoff?.let(::payoff))
}
