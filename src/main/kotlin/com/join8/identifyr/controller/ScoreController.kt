package com.join8.identifyr.controller

import com.join8.identifyr.model.dto.request.QuestionnaireRequest
import com.join8.identifyr.model.dto.response.ScoreResponse
import com.join8.identifyr.service.ScoreService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

// El context-path del servidor ya es /api/v1, por eso aquí solo va /scores
@RestController
@RequestMapping("/scores")
class ScoreController(
    private val scoreService: ScoreService
) {

    @PostMapping("/calculate")
    fun calculate(
        @Valid @RequestBody request: QuestionnaireRequest
    ): ResponseEntity<ScoreResponse> {
        return ResponseEntity.ok(scoreService.calculate(request))
    }
}
