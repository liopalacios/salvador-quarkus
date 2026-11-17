package com.join8.identifyr.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.join8.identifyr.exception.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import java.time.LocalDateTime

/** Escribe los errores de la capa de seguridad con el mismo formato que GlobalExceptionHandler. */
class SecurityErrorWriter(private val objectMapper: ObjectMapper) {
    fun write(response: HttpServletResponse, status: HttpStatus, code: String, message: String) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        objectMapper.writeValue(
            response.outputStream,
            ErrorResponse(timestamp = LocalDateTime.now(), status = status.value(), error = code, message = message, path = null)
        )
    }
}

/**
 * 401: token ausente, mal formado, con firma inválida o vencido. No se devuelve el detalle de la
 * excepción (podría repetir partes del token) ni se registra el header Authorization.
 */
class JsonAuthenticationEntryPoint(private val writer: SecurityErrorWriter) : AuthenticationEntryPoint {
    override fun commence(request: HttpServletRequest, response: HttpServletResponse, authException: AuthenticationException) {
        val hasToken = !request.getHeader(HttpHeaders.AUTHORIZATION).isNullOrBlank()
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, if (hasToken) "Bearer error=\"invalid_token\"" else "Bearer")
        writer.write(
            response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
            if (hasToken) "Token inválido o expirado" else "Se requiere autenticación"
        )
    }
}

/** 403: token válido sin el rol requerido. */
class JsonAccessDeniedHandler(private val writer: SecurityErrorWriter) : AccessDeniedHandler {
    override fun handle(request: HttpServletRequest, response: HttpServletResponse, ex: AccessDeniedException) =
        writer.write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "No tienes permisos para esta operación")
}
