package com.join8.identifyr.controller

import com.join8.identifyr.model.dto.request.LoanPaymentRegisterRequest
import com.join8.identifyr.model.dto.request.LoanPaymentRejectRequest
import com.join8.identifyr.model.dto.response.LoanPaymentResponse
import com.join8.identifyr.model.entity.PaymentType
import com.join8.identifyr.security.AuthenticatedUser
import com.join8.identifyr.exception.ForbiddenException
import com.join8.identifyr.service.LoanPaymentService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
class LoanPaymentController(private val loanPaymentService: LoanPaymentService) {

    // Cliente: el préstamo debe ser suyo (si no, 404). 201 · 400 · 404 · 409 pago pendiente o código usado · 422
    @PostMapping("/loans/{loanId}/payments")
    fun register(
        @PathVariable loanId: UUID,
        @Valid @RequestBody request: LoanPaymentRegisterRequest,
        @AuthenticationPrincipal auth: AuthenticatedUser
    ): ResponseEntity<LoanPaymentResponse> {
        val userId = auth.userId ?: throw ForbiddenException("Acceso denegado")
        val response = loanPaymentService.register(loanId, userId, PaymentType.valueOf(request.tipo!!), request.codigoOperacion!!)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    // Solo supervisor (ver SecurityConfig). estado = PENDIENTE (por defecto) | CONFIRMADO | RECHAZADO
    @GetMapping("/loan-payments")
    fun list(@RequestParam(required = false) estado: String?): ResponseEntity<List<LoanPaymentResponse>> =
        ResponseEntity.ok(loanPaymentService.list(estado))

    // Solo supervisor. 200 · 404 · 409 ya no está pendiente
    @PostMapping("/loan-payments/{id}/confirm")
    fun confirm(@PathVariable id: UUID, @AuthenticationPrincipal auth: AuthenticatedUser): ResponseEntity<LoanPaymentResponse> =
        ResponseEntity.ok(loanPaymentService.confirm(id, auth.subject))

    // Solo supervisor. Body opcional {motivo}. 200 · 400 · 404 · 409
    @PostMapping("/loan-payments/{id}/reject")
    fun reject(
        @PathVariable id: UUID,
        @Valid @RequestBody(required = false) request: LoanPaymentRejectRequest?,
        @AuthenticationPrincipal auth: AuthenticatedUser
    ): ResponseEntity<LoanPaymentResponse> =
        ResponseEntity.ok(loanPaymentService.reject(id, request?.motivo, auth.subject))
}
