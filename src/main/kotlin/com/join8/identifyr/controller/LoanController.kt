package com.join8.identifyr.controller

import com.join8.identifyr.model.dto.request.LoanRegisterRequest
import com.join8.identifyr.model.dto.response.LoanRegisterResponse
import com.join8.identifyr.service.LoanService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

// El context-path del servidor ya es /api/v1
@RestController
@RequestMapping("/loans")
class LoanController(
    private val loanService: LoanService
) {

    // Solo supervisor (ver SecurityConfig). 422 (no 404) si la solicitud no existe.
    // created_by = "sub" del token; null mientras se permita el acceso sin token.
    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: LoanRegisterRequest,
        @AuthenticationPrincipal jwt: Jwt?
    ): ResponseEntity<LoanRegisterResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(loanService.register(request, jwt?.subject))
}
