package com.join8.identifyr.controller

import com.join8.identifyr.model.dto.request.LoanRequestRegisterRequest
import com.join8.identifyr.model.dto.request.LoanRequestRejectRequest
import com.join8.identifyr.model.dto.response.LoanRequestItemResponse
import com.join8.identifyr.model.dto.response.LoanRequestRegisterResponse
import com.join8.identifyr.model.dto.response.LoanRequestRejectResponse
import com.join8.identifyr.security.AccessGuard
import com.join8.identifyr.security.AuthenticatedUser
import com.join8.identifyr.service.LoanRequestService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.*

// El context-path del servidor ya es /api/v1
@RestController
@RequestMapping("/loan-requests")
class LoanRequestController(
    private val loanRequestService: LoanRequestService
) {

    // 422 (no 404) si el usuario no existe: la app trata un 404 de esta ruta como éxito simulado.
    // idUsuario y numCelular deben ser los del token (si no, 403); el servicio usa los del token.
    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: LoanRequestRegisterRequest,
        @AuthenticationPrincipal auth: AuthenticatedUser
    ): ResponseEntity<LoanRequestRegisterResponse> {
        AccessGuard.requireOwner(auth, request.idUsuario)
        AccessGuard.requirePhoneOwner(auth, request.numCelular)
        val trusted = request.copy(idUsuario = auth.userId.toString(), numCelular = auth.phone)
        return ResponseEntity.status(HttpStatus.CREATED).body(loanRequestService.register(trusted))
    }

    // Solo supervisor (ver SecurityConfig). Body opcional {motivo}. 200 · 400 motivo > 255 · 404 · 409 no PENDIENTE
    @PostMapping("/{id}/reject")
    fun reject(
        @PathVariable id: UUID,
        @Valid @RequestBody(required = false) request: LoanRequestRejectRequest?,
        @AuthenticationPrincipal auth: AuthenticatedUser
    ): ResponseEntity<LoanRequestRejectResponse> =
        ResponseEntity.ok(loanRequestService.reject(id, request?.motivo, auth.subject))

    // Solo supervisor (ver SecurityConfig). Arreglo sin envoltorio; sin resultados -> 200 con [].
    // fecha (yyyy-MM-dd) opcional: solo las solicitudes de ese día; sin fecha, las de los últimos 30 días.
    @GetMapping
    fun list(
        @RequestParam(required = false) estado: String?,
        @RequestParam(required = false) fecha: String?
    ): ResponseEntity<List<LoanRequestItemResponse>> =
        ResponseEntity.ok(loanRequestService.list(estado, fecha))
}
