package com.join8.identifyr.model.dto.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UserRegisterRequest(

    @field:NotBlank(message = "Celular es obligatorio")
    @field:Size(min = 9, max = 9, message = "Celular debe tener 9 dígitos")
    @field:Pattern(regexp = "^[0-9]+$", message = "Celular solo debe contener números")
    val phone: String,

    @field:NotBlank(message = "Imagen frontal documento")
    val dniFrontPhotoId: String,

    @field:NotBlank(message = "Imagen reverso documento")
    val dniBackPhotoId: String,

)