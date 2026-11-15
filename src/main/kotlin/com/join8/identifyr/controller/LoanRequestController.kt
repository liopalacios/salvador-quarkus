package com.join8.identifyr.controller

import com.join8.identifyr.model.dto.request.LoanRequestRegisterRequest
import com.join8.identifyr.model.dto.response.LoanRequestItemResponse
import com.join8.identifyr.model.dto.response.LoanRequestRegisterResponse
import com.join8.identifyr.service.LoanRequestService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

// El context-path del servidor ya es /api/v1
@RestController
@RequestMapping("/loan-requests")
class LoanRequestController(
    private val loanRequestService: LoanRequestService
) {

    // 422 (no 404) si el usuario no existe: la app trata un 404 de esta ruta como éxito simulado
    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: LoanRequestRegisterRequest
    ): ResponseEntity<LoanRequestRegisterResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(loanRequestService.register(request))

    // Solo supervisor (ver SecurityConfig). Arreglo sin envoltorio; sin resultados -> 200 con [].
    @GetMapping
    fun list(@RequestParam(required = false) estado: String?): ResponseEntity<List<LoanRequestItemResponse>> =
        ResponseEntity.ok(loanRequestService.list(estado))
}
