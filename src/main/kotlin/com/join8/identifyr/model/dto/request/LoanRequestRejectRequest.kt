package com.join8.identifyr.model.dto.request

import jakarta.validation.constraints.Size

/** Body (opcional) de POST /loan-requests/{id}/reject. Motivo vacío o solo espacios = sin motivo. */
data class LoanRequestRejectRequest(
    @field:Size(max = 255, message = "no puede tener más de 255 caracteres")
    val motivo: String? = null
)
