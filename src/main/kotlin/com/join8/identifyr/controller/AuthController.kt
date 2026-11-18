package com.join8.identifyr.controller

import com.join8.identifyr.exception.NotImplementedException
import com.join8.identifyr.model.dto.request.LoginRequest
import com.join8.identifyr.model.dto.request.OtpSendRequest
import com.join8.identifyr.model.dto.request.OtpVerifyRequest
import com.join8.identifyr.model.dto.request.RegisterPasswordRequest
import com.join8.identifyr.model.dto.request.StaffLoginRequest
import com.join8.identifyr.model.dto.response.AuthTokenResponse
import com.join8.identifyr.model.entity.Role
import com.join8.identifyr.service.AuthService
import com.join8.identifyr.service.StaffAuthService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Endpoints públicos de autenticación (ver SecurityConfig.PUBLIC_ENDPOINTS), con rate limit por IP.
 * La IP es request.remoteAddr: en Azure la resuelve Tomcat con el X-Forwarded-For del ingress.
 */
@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
    private val staffAuthService: StaffAuthService
) {

    // 201 token · 400 formato · 403 plazo vencido · 404 usuario inexistente · 409 ya tiene contraseña
    @PostMapping("/register-password")
    fun registerPassword(
        @Valid @RequestBody request: RegisterPasswordRequest,
        http: HttpServletRequest
    ): ResponseEntity<AuthTokenResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(authService.registerPassword(request, http.remoteAddr))

    // 200 token · 400 formato · 401 credenciales inválidas · 423 bloqueado
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest, http: HttpServletRequest): ResponseEntity<AuthTokenResponse> =
        ResponseEntity.ok(authService.login(request, http.remoteAddr))

    // Igual que /login pero solo emite token si el usuario tiene role = SUPERVISOR
    @PostMapping("/supervisor/login")
    fun supervisorLogin(@Valid @RequestBody request: LoginRequest, http: HttpServletRequest): ResponseEntity<AuthTokenResponse> =
        ResponseEntity.ok(authService.login(request, http.remoteAddr, requiredRole = Role.SUPERVISOR))

    // Personal interno (tabla staff_users): usuario + contraseña fuerte. 200 token · 400 formato · 401 · 423
    @PostMapping("/staff/login")
    fun staffLogin(@Valid @RequestBody request: StaffLoginRequest, http: HttpServletRequest): ResponseEntity<AuthTokenResponse> =
        ResponseEntity.ok(staffAuthService.login(request, http.remoteAddr))

    // --- Siguiente fase: OTP validado en el backend (hoy lo valida la app). Contrato reservado, responde 501. ---

    @PostMapping("/otp/send")
    fun otpSend(@Valid @RequestBody request: OtpSendRequest): ResponseEntity<Unit> =
        throw NotImplementedException("El envío de OTP desde el backend aún no está disponible")

    @PostMapping("/otp/verify")
    fun otpVerify(@Valid @RequestBody request: OtpVerifyRequest): ResponseEntity<Unit> =
        throw NotImplementedException("La verificación de OTP desde el backend aún no está disponible")
}
