package com.join8.identifyr.model.dto.request

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.time.LocalDate

/**
 * Las claves JSON (idSolicitud, monto, ...) son el contrato con la app Android: no renombrar.
 * Todos los campos son nullable para que un faltante llegue a Bean Validation y se informe por campo.
 * Las fechas llegan como "YYYY-MM-DD"; un formato inválido se responde 400 indicando el campo.
 */
data class LoanRegisterRequest(
    @field:NotBlank(message = "es obligatorio")
    @field:Pattern(
        regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
        message = "debe ser un UUID válido"
    )
    val idSolicitud: String? = null,

    @field:NotNull(message = "es obligatorio")
    @field:Min(value = 1, message = "debe ser mayor que 0")
    val monto: Int? = null,

    // Se recorta en el servicio; aquí solo se rechaza vacío o solo espacios
    @field:NotBlank(message = "es obligatorio")
    val codigoOperacion: String? = null,

    @field:NotNull(message = "es obligatorio")
    @field:Min(value = 0, message = "debe ser mayor o igual a 0")
    val puntaje: Int? = null,

    @field:NotBlank(message = "es obligatorio")
    @field:Pattern(regexp = "^(ORO|PLATA|BRONCE)$", message = "solo se permite ORO, PLATA o BRONCE")
    val categoria: String? = null,

    @field:NotNull(message = "es obligatorio")
    @field:Min(value = 0, message = "debe ser mayor o igual a 0")
    val interes: Int? = null,

    @field:NotNull(message = "es obligatorio")
    val fechaPago: LocalDate? = null,

    @field:NotNull(message = "es obligatorio")
    val fechaPagoApp: LocalDate? = null
)
