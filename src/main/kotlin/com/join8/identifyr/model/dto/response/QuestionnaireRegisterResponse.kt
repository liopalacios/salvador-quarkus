package com.join8.identifyr.model.dto.response

import java.time.Instant
import java.util.*

data class QuestionnaireRegisterResponse(
    val questionnaireId: Long,
    val userId: UUID,
    val status: String,
    val createdAt: Instant
)
