package com.join8.identifyr.model.dto.response

import java.math.BigDecimal
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
    /** Porcentaje: 18.00 = 18% */
    val tasaInteres: BigDecimal,
    val montoInteres: BigDecimal,
    val montoTotal: BigDecimal,
    val frecuenciaCuota: Int,
    val numeroCuotas: Int,
    val plazoDias: Int,
    val cuotas: List<LoanInstallmentResponse>
)

data class LoanInstallmentResponse(
    val numeroCuota: Int,
    val fechaVencimiento: LocalDate,
    val montoCuota: BigDecimal,
    val estado: String
)
