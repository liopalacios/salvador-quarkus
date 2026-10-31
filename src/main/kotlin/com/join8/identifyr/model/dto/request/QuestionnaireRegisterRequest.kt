package com.join8.identifyr.model.dto.request

import jakarta.validation.constraints.NotBlank

/**
 * Cada envío trae solo su bloque: todos los campos excepto userId son opcionales.
 * Un campo ausente, null o vacío conserva el valor del registro activo anterior.
 */
data class QuestionnaireRegisterRequest(
    @field:NotBlank(message = "El userId es obligatorio")
    val userId: String? = null,

    // Bloque 1
    val employmentType: String? = null,
    val employmentTenure: String? = null,
    val residentialTenure: String? = null,
    val housingType: String? = null,
    val dependents: String? = null,

    // Bloque 2
    val email: String? = null,
    val address: String? = null,
    val ruc: String? = null,
    val driverLicense: String? = null,
    val vehiclePlate: String? = null,
    val educationLevel: String? = null,
    val workplace: String? = null,
    val workApps: String? = null,
    val accountStatementPhotoId: String? = null
)
