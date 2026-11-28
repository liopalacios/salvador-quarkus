package com.join8.identifyr.model.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** POST /loans/{loanId}/payments. El monto no se envía: lo calcula el servidor según [tipo]. */
data class LoanPaymentRegisterRequest(
    @field:NotBlank(message = "es obligatorio")
    @field:Pattern(regexp = "^(CUOTA|TOTAL)$", message = "debe ser CUOTA o TOTAL")
    val tipo: String? = null,

    @field:NotBlank(message = "es obligatorio")
    @field:Size(max = 50, message = "no puede tener más de 50 caracteres")
    val codigoOperacion: String? = null
)

/** Body (opcional) de POST /loan-payments/{id}/reject. */
data class LoanPaymentRejectRequest(
    @field:Size(max = 255, message = "no puede tener más de 255 caracteres")
    val motivo: String? = null
)
