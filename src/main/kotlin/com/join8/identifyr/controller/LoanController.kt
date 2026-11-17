package com.join8.identifyr.controller

import com.join8.identifyr.model.dto.request.LoanRegisterRequest
import com.join8.identifyr.model.dto.response.CurrentLoanResponse
import com.join8.identifyr.model.dto.response.LoanRegisterResponse
import com.join8.identifyr.security.AccessGuard
import com.join8.identifyr.security.AuthenticatedUser
import com.join8.identifyr.service.LoanService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

// El context-path del servidor ya es /api/v1
@RestController
@RequestMapping("/loans")
class LoanController(
    private val loanService: LoanService
) {

    // Solo supervisor (ver SecurityConfig). 201 / 400 / 404 / 409 / 422 (ver LoanService.register).
    // created_by = "sub" del token del supervisor.
    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: LoanRegisterRequest,
        @AuthenticationPrincipal auth: AuthenticatedUser
    ): ResponseEntity<LoanRegisterResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(loanService.register(request, auth.subject))

    // App del cliente: {phone} debe ser el claim "phone" del token (o supervisor). Con rate limit por IP.
    @GetMapping("/current/phone/{phone}")
    fun current(
        @PathVariable phone: String,
        @AuthenticationPrincipal auth: AuthenticatedUser
    ): ResponseEntity<CurrentLoanResponse> {
        AccessGuard.requirePhoneOwnerOrSupervisor(auth, phone)
        return ResponseEntity.ok(loanService.current(phone))
    }
}
