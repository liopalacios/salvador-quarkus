package com.join8.identifyr.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Rechaza (403 HTTPS_REQUIRED, sin redirigir) las peticiones que no llegaron por HTTPS.
 * Detrás del ingress de Azure, request.isSecure lo resuelve Tomcat con el X-Forwarded-Proto del proxy
 * (server.forward-headers-strategy=native). El health check queda exento: las sondas van por HTTP interno.
 */
class HttpsEnforcementFilter(private val writer: SecurityErrorWriter) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI.removePrefix(request.contextPath).startsWith("/actuator/health")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        if (request.isSecure) chain.doFilter(request, response)
        else writer.write(response, HttpStatus.FORBIDDEN, "HTTPS_REQUIRED", "Solo se aceptan conexiones HTTPS")
    }
}
