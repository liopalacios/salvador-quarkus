package com.join8.identifyr.model.dto.response

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

/** Claves en español: son el contrato con la app. Montos con 2 decimales. */
data class LoanRegisterResponse(
    val idPrestamo: UUID,
    val idSolicitud: UUID,
    val codigoOperacion: String,
    /** Hora del servidor en America/Lima, ej. 2026-11-14T10:32:05-05:00 */
    val fechaRegistroOperacion: OffsetDateTime,
    val fechaDesembolso: LocalDate,
    val montoDesembolsado: BigDecimal,
    /** TEA en porcentaje: 114.68 = 114.68% */
    val tasaInteres: BigDecimal,
    /** Interés total del crédito (sin comisión ni APIs). */
    val montoInteres: BigDecimal,
    val comisionTotal: BigDecimal,
    val apisTotal: BigDecimal,
    /** Cuota fija (igual en todas). */
    val cuota: BigDecimal,
    /** Total a pagar = número de cuotas × cuota. */
    val montoTotal: BigDecimal,
    /** TCEA en porcentaje con 2 decimales: 2761.72 = 2,761.72% */
    val tcea: BigDecimal,
    val frecuenciaCuota: Int,
    val numeroCuotas: Int,
    /** Días hasta la última cuota (puede pasar de 60 si el vencimiento se corre a día hábil). */
    val plazoDias: Int,
    val cuotas: List<LoanInstallmentResponse>
)

/** Cuota del cronograma. El desglose se omite en cuotas sin recalcular con el motor. */
data class LoanInstallmentResponse(
    val numeroCuota: Int,
    val fechaVencimiento: LocalDate,
    val montoCuota: BigDecimal,
    /** ACTIVA | PAGADA | PENDIENTE */
    val estado: String,
    val saldoInicial: BigDecimal? = null,
    val interes: BigDecimal? = null,
    val capital: BigDecimal? = null,
    val comision: BigDecimal? = null,
    val apis: BigDecimal? = null,
    val saldoFinal: BigDecimal? = null,
    /** Solo en PAGADA: monto realmente pagado (con descuento por anticipo o recargo por atraso). */
    val montoPagado: BigDecimal? = null,
    val fechaPago: Instant? = null
)

/** Liquidación de una cuota a una fecha de pago (CreditEngine F21–F27). */
data class InstallmentQuoteResponse(
    val numeroCuota: Int,
    /** ANTICIPADA | A_TIEMPO | CON_ATRASO */
    val estadoPago: String,
    val diasUsados: Int,
    val diasAtraso: Int,
    val capital: BigDecimal,
    val interes: BigDecimal,
    val interesMoratorio: BigDecimal,
    val comision: BigDecimal,
    val apis: BigDecimal,
    val montoAPagar: BigDecimal,
    val cuotaContrato: BigDecimal,
    /** Negativo = ahorro por pagar antes; positivo = recargo por atraso. */
    val diferencia: BigDecimal
)

/** Cuánto paga hoy el cliente: la cuota ACTIVA (opción a) y cancelar el 100% (opción b, F28). */
data class PaymentQuoteResponse(
    val fechaPago: LocalDate,
    val cuota: InstallmentQuoteResponse?,
    val total: PayoffQuoteResponse?
)

data class PayoffQuoteResponse(
    val monto: BigDecimal,
    val capital: BigDecimal,
    val interes: BigDecimal,
    val interesMoratorio: BigDecimal,
    val comision: BigDecimal,
    val apis: BigDecimal,
    /** Lo que se pagaría por esas cuotas según el contrato; la diferencia es el ahorro (o recargo). */
    val totalContrato: BigDecimal,
    val diferencia: BigDecimal,
    val cuotas: List<InstallmentQuoteResponse>
)
