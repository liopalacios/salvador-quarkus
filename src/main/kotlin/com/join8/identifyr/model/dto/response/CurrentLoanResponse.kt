package com.join8.identifyr.model.dto.response

import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

enum class CurrentLoanType { PRESTAMO_ACTIVO, SOLICITUD }

/**
 * GET /loans/current/phone/{phone}. Los campos que no aplican al [tipo] son null y se omiten del JSON.
 * - PRESTAMO_ACTIVO: datos del préstamo y de la próxima cuota sin pagar.
 * - SOLICITUD: solicitud PENDIENTE sin préstamo todavía.
 */
data class CurrentLoanResponse(
    val tipo: CurrentLoanType,
    val idSolicitud: UUID,
    val estado: String,
    val carteraDigital: String,
    val montoSolicitado: Int? = null,
    val frecuenciaCuota: Int? = null,

    val idPrestamo: UUID? = null,
    val codigoOperacion: String? = null,
    val fechaDesembolso: LocalDate? = null,
    val montoDesembolsado: BigDecimal? = null,
    val montoTotal: BigDecimal? = null,
    val saldoPendiente: BigDecimal? = null,
    val montoCuota: BigDecimal? = null,
    val numeroCuota: Int? = null,
    val totalCuotas: Int? = null,
    val fechaVencimiento: LocalDate? = null,
    val estadoCuota: String? = null
)
