package com.join8.identifyr.model.dto.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UserRegisterRequest(
    @field:NotBlank(message = "DNI es obligatorio")
    @field:Size(min = 8, max = 8, message = "DNI debe tener 8 dígitos")
    @field:Pattern(regexp = "^[0-9]+$", message = "DNI solo debe contener números")
    val dni: String,

    @field:NotBlank(message = "Nombre completo es obligatorio")
    @field:Size(min = 3, max = 255, message = "Nombre debe tener entre 3 y 255 caracteres")
    val fullName: String,

    @field:NotBlank(message = "Celular es obligatorio")
    @field:Size(min = 9, max = 9, message = "Celular debe tener 9 dígitos")
    @field:Pattern(regexp = "^[0-9]+$", message = "Celular solo debe contener números")
    val phone: String,

    @field:NotBlank(message = "Email es obligatorio")
    @field:Email(message = "Email debe tener un formato válido")
    val email: String,

    @field:NotBlank(message = "Contraseña es obligatoria")
    @field:Size(min = 6, message = "Contraseña debe tener al menos 6 caracteres")
    val password: String,

    val address: String? = null,
    val nationality: String? = null
)