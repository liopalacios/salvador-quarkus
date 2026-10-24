package com.join8.identifyr.model.dto.request

import jakarta.validation.constraints.NotBlank

data class QuestionnaireRequest(
    @field:NotBlank(message = "El userId es obligatorio")
    val userId: String,

    @field:NotBlank(message = "La pregunta 1 (tipo de empleo) es obligatoria")
    val employmentType: String,

    @field:NotBlank(message = "La pregunta 2 (antigüedad laboral) es obligatoria")
    val employmentTenure: String,

    @field:NotBlank(message = "La pregunta 3 (antigüedad residencial) es obligatoria")
    val residentialTenure: String,

    @field:NotBlank(message = "La pregunta 4 (tipo de vivienda) es obligatoria")
    val housingType: String,

    @field:NotBlank(message = "La pregunta 5 (dependientes) es obligatoria")
    val dependents: String
)
