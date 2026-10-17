package com.join8.identifyr.model.dto.request

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Past
import jakarta.validation.constraints.PositiveOrZero
import java.time.LocalDate

data class ContextRequest(
    @field:NotNull(message = "La fecha de nacimiento es obligatoria")
    @field:Past(message = "La fecha de nacimiento debe ser en el pasado")
    val birthDate: LocalDate,

    val address: String? = null,
    val district: String? = null,
    val housingType: HousingType? = null,
    val educationLevel: EducationLevel? = null,

    @field:PositiveOrZero(message = "El número de dependientes no puede ser negativo")
    val dependentsCount: Int = 0,

    val economicActivity: String? = null,
    val economicActivityType: EconomicActivityType? = null,

    @field:PositiveOrZero(message = "La antigüedad en la actividad no puede ser negativa")
    val economicActivityTenureMonths: Int? = null,

    @field:PositiveOrZero(message = "La antigüedad residencial no puede ser negativa")
    val residentialStabilityMonths: Int? = null
)

enum class HousingType { OWNED, RENTED, FAMILY, ASSIGNED, OTHER }
enum class EducationLevel { PRIMARY, SECONDARY, TECHNICAL, UNIVERSITY, POSTGRADUATE, NONE }
enum class EconomicActivityType { COMMERCE, DELIVERY, TAXI, INDEPENDENT, EMPLOYED, UNEMPLOYED, OTHER }