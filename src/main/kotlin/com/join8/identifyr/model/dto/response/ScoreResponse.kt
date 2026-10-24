package com.join8.identifyr.model.dto.response

data class ScoreResponse(
    val scoreValue: Int,
    val levelName: String,
    val maxLoanAmount: Int,
    val reasons: List<String>
)
