package com.join8.identifyr.model.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Solo el celular es obligatorio. Las fotos del DNI ya no llegan aquí (se envían luego con
 * PUT /users/{userId}/dni-photos); si una versión anterior de la app las envía, se guardan.
 */
data class UserRegisterRequest(

    @field:NotBlank(message = "Celular es obligatorio")
    @field:Pattern(regexp = PHONE_REGEX, message = "Celular debe tener 9 dígitos")
    val phone: String? = null,

    @field:Size(max = 255, message = "no puede tener más de 255 caracteres")
    val dniFrontPhotoId: String? = null,

    @field:Size(max = 255, message = "no puede tener más de 255 caracteres")
    val dniBackPhotoId: String? = null,
)
