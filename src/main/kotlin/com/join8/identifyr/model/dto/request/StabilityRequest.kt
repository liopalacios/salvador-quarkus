package com.join8.identifyr.model.dto.request

import jakarta.validation.constraints.PositiveOrZero

data class StabilityRequest(
    @field:PositiveOrZero(message = "La antigüedad del celular no puede ser negativa")
    val phoneAgeMonths: Int? = null,

    @field:PositiveOrZero(message = "La antigüedad del email no puede ser negativa")
    val emailAgeMonths: Int? = null,

    @field:PositiveOrZero(message = "La antigüedad del dispositivo no puede ser negativa")
    val deviceTenureMonths: Int? = null,

    val deviceConsistencyCount: Int = 1,
    val lastDeviceId: String? = null
)