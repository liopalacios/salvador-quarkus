package com.join8.identifyr.model.dto.response

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*

/** Pago tal como lo ven el cliente (registro, inicio) y el supervisor (listado, confirmar/rechazar). */
data class LoanPaymentResponse(
    val idPago: UUID,
    val idPrestamo: UUID,
    /** CUOTA | TOTAL */
    val tipo: String,
    /** Monto liquidado por el motor con la fecha de pago (con descuento por anticipo o recargo por atraso). */
    val monto: BigDecimal,
    /** Solo en CUOTA */
    val numeroCuota: Int? = null,
    val codigoOperacion: String,
    /** PENDIENTE | CONFIRMADO | RECHAZADO */
    val estado: String,
    val fechaRegistro: Instant,
    /** Solo en el listado del supervisor */
    val numCelular: String? = null,
    val motivoRechazo: String? = null,
    /** Fecha (Lima) con la que se liquidó: el día en que el cliente informó el pago. */
    val fechaPago: LocalDate? = null,
    // Desglose del monto
    val capital: BigDecimal? = null,
    val interes: BigDecimal? = null,
    val interesMoratorio: BigDecimal? = null,
    val comision: BigDecimal? = null,
    val apis: BigDecimal? = null,
    /** Cuotas que cubre el pago (una en CUOTA; todas las que faltaban en TOTAL). */
    val cuotas: List<Int>? = null
)
