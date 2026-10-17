package com.join8.identifyr.model.dto.response

import java.util.*

data class StabilityResponse(
    val userId: UUID,
    val phoneAgeMonths: Int?,
    val emailAgeMonths: Int?,
    val deviceTenureMonths: Int?,
    val deviceConsistencyCount: Int
)