package com.join8.identifyr.model.dto.response

import java.time.Instant
import java.util.*

/** Respuesta de POST /loan-requests/{id}/reject. [motivo] se omite si no se indicó. */
data class LoanRequestRejectResponse(
    val idSolicitud: UUID,
    val estado: String,
    val motivo: String?,
    val fechaRechazo: Instant
)
