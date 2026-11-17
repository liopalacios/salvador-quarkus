package com.join8.identifyr.controller

import com.join8.identifyr.model.dto.request.QuestionnaireRegisterRequest
import com.join8.identifyr.model.dto.response.QuestionnaireRegisterResponse
import com.join8.identifyr.security.AccessGuard
import com.join8.identifyr.security.AuthenticatedUser
import com.join8.identifyr.service.QuestionnaireService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

// El context-path del servidor ya es /api/v1
@RestController
@RequestMapping("/questionnaires")
class QuestionnaireController(
    private val questionnaireService: QuestionnaireService
) {

    // Siempre 201: cada envío inserta un registro nuevo. body.userId debe ser el "sub" del token (si no, 403).
    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: QuestionnaireRegisterRequest,
        @AuthenticationPrincipal auth: AuthenticatedUser
    ): ResponseEntity<QuestionnaireRegisterResponse> {
        AccessGuard.requireOwner(auth, request.userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(questionnaireService.register(request))
    }
}
