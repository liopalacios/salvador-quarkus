package com.join8.identifyr.model.dto.response

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant
import java.util.*

/** Claves en español, como en el registro: son el contrato con la pantalla del supervisor. */
data class LoanRequestItemResponse(
    val id: UUID,
    val idUsuario: UUID,
    val numCelular: String,
    val montoSolicitado: Int,
    val puntaje: Int,
    val categoria: String,
    val tipoDeposito: String,
    val estado: String,
    // Se envía siempre, aunque sea null (la config global omite los null)
    @field:JsonInclude(JsonInclude.Include.ALWAYS)
    val codigoOperacion: String?,
    val fechaRegistro: Instant,
    val fechaActualizacion: Instant
)

data class LoanRequestListResponse(
    val items: List<LoanRequestItemResponse>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
    /** Siempre los tres estados (PENDIENTE, PROCESADA, RECHAZADA), con 0 si no hay ninguna. */
    val conteos: Map<String, Long>
)
