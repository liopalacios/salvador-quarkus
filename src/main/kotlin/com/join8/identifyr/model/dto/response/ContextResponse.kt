package com.join8.identifyr.model.dto.response

import java.time.LocalDate
import java.util.*

data class ContextResponse(
    val userId: UUID,
    val birthDate: LocalDate,
    val address: String?,
    val district: String?,
    val housingType: String?,
    val educationLevel: String?,
    val dependentsCount: Int,
    val economicActivity: String?,
    val economicActivityType: String?,
    val economicActivityTenureMonths: Int?,
    val residentialStabilityMonths: Int?
)