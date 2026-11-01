package com.join8.identifyr.model.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.time.LocalDate
import java.util.*

/** Claves en español: son el contrato con la app. */
data class LoanRegisterResponse(
    val id: UUID,
    val idSolicitud: UUID,
    val monto: Int,
    val interes: Int,
    @get:JsonProperty("totalAPagar")
    val totalAPagar: Int,
    val codigoOperacion: String,
    val fechaPago: LocalDate,
    val fechaPagoApp: LocalDate,
    val estado: String,
    val fechaRegistro: Instant
)
