package com.join8.identifyr.model.dto.response

import com.fasterxml.jackson.annotation.JsonInclude
import java.util.*

/**
 * Item de GET /loan-requests?estado=... (la respuesta es un arreglo de estos, sin envoltorio).
 * Exactamente los campos que usa la pantalla del supervisor: no agregar datos sensibles.
 */
data class LoanRequestItemResponse(
    val idSolicitud: UUID,
    /** 9 dígitos, sin prefijo. */
    val numCelular: String,
    /** Soles, entero. */
    val montoSolicitado: Int,
    val puntaje: Int,
    /** ORO | PLATA | BRONCE */
    val categoria: String,
    /** PENDIENTE | PROCESADA | RECHAZADA, siempre igual al filtro pedido. */
    val estado: String,
    /** Solo en PROCESADA; null en los otros estados. Se envía siempre, aunque sea null (la config global omite los null). */
    @field:JsonInclude(JsonInclude.Include.ALWAYS)
    val codigoOperacion: String?,
    /** YAPE | PLIN */
    val carteraDigital: String
)
