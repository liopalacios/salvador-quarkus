package com.join8.identifyr.model.dto.response

import java.time.Instant
import java.util.*

data class LoanRequestRegisterResponse(
    val id: UUID,
    val status: String,
    val createdAt: Instant
)
