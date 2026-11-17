package com.join8.identifyr.controller

import com.join8.identifyr.model.dto.request.QuestionnaireRequest
import com.join8.identifyr.model.dto.response.ScoreResponse
import com.join8.identifyr.security.AccessGuard
import com.join8.identifyr.security.AuthenticatedUser
import com.join8.identifyr.service.QuestionnaireService
import com.join8.identifyr.service.ScoreService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.*

// El context-path del servidor ya es /api/v1, por eso aquí solo va /scores
@RestController
@RequestMapping("/scores")
class ScoreController(
    private val scoreService: ScoreService,
    private val questionnaireService: QuestionnaireService
) {

    // Legado: la app ya no lo usa; el score se obtiene con GET /scores/{userId}
    @PostMapping("/calculate")
    fun calculate(
        @Valid @RequestBody request: QuestionnaireRequest,
        @AuthenticationPrincipal auth: AuthenticatedUser
    ): ResponseEntity<ScoreResponse> {
        AccessGuard.requireOwner(auth, request.userId)
        return ResponseEntity.ok(scoreService.calculate(request))
    }

    // Calcula con el cuestionario activo (estado 'A') guardado en la base de datos. {userId} = "sub" o supervisor.
    @GetMapping("/{userId}")
    fun getScore(
        @PathVariable userId: UUID,
        @AuthenticationPrincipal auth: AuthenticatedUser
    ): ResponseEntity<ScoreResponse> {
        AccessGuard.requireOwnerOrSupervisor(auth, userId)
        return ResponseEntity.ok(questionnaireService.scoreFor(userId))
    }
}
