package com.join8.identifyr.controller

import com.join8.identifyr.model.dto.request.LoanRequestRegisterRequest
import com.join8.identifyr.model.dto.response.LoanRequestListResponse
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

    // Solo supervisor (ver SecurityConfig). Sin resultados -> 200 con items vacío, nunca 404.
    @GetMapping
    fun list(
        @RequestParam(required = false) estado: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<LoanRequestListResponse> =
        ResponseEntity.ok(loanRequestService.list(estado, page, size))
}
