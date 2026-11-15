package com.join8.identifyr.model.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/**
 * El cliente envía SOLO la solicitud y el código de operación. Monto, plan, fechas, etc. salen de la BD.
 * Cualquier otro campo del JSON se ignora (spring.jackson.deserialization.fail-on-unknown-properties=false)
 * y nunca se usa para el cálculo.
 */
data class LoanRegisterRequest(
    @field:NotBlank(message = "es obligatorio")
    @field:Pattern(
        regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
        message = "debe ser un UUID válido"
    )
    val idSolicitud: String? = null,

    // Se recorta en el servicio; aquí solo se rechaza vacío o solo espacios
    @field:NotBlank(message = "es obligatorio")
    val codigoOperacion: String? = null
)
