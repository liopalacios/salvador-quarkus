package com.join8.identifyr.model.dto.request

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

/**
 * Las claves JSON (idUsuario, numCelular, ...) son el contrato con la app Android: no renombrar.
 * Todos los campos son nullable para que un faltante llegue a Bean Validation y se informe por campo.
 */
data class LoanRequestRegisterRequest(
    @field:NotBlank(message = "es obligatorio")
    @field:Pattern(
        regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
        message = "debe ser un UUID válido"
    )
    val idUsuario: String? = null,

    @field:NotBlank(message = "es obligatorio")
    @field:Pattern(regexp = "^9\\d{8}$", message = "debe ser un celular de 9 dígitos que empiece con 9")
    val numCelular: String? = null,

    @field:NotBlank(message = "es obligatorio")
    @field:Pattern(regexp = "^(YAPE|PLIN)$", message = "solo se permite YAPE o PLIN")
    val tipoDeposito: String? = null,

    @field:NotBlank(message = "es obligatorio")
    @field:Pattern(regexp = "^(ORO|PLATA|BRONCE)$", message = "solo se permite ORO, PLATA o BRONCE")
    val categoria: String? = null,

    @field:NotNull(message = "es obligatorio")
    @field:Min(value = 0, message = "debe ser mayor o igual a 0")
    val puntaje: Int? = null,

    @field:NotNull(message = "es obligatorio")
    @field:Min(value = 1, message = "debe ser mayor que 0")
    val montoSolicitado: Int? = null
)
