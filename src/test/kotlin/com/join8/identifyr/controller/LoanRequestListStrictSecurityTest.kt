package com.join8.identifyr.controller

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test

/** Modo definitivo (cuando exista el login de supervisor): sin token -> 401. */
@SpringBootTest(properties = ["security.supervisor.allow-anonymous=false"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoanRequestListStrictSecurityTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Value("\${jwt.secret}") lateinit var jwtSecret: String

    private fun token(roles: List<String>): String {
        val encoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(SecretKeySpec(jwtSecret.toByteArray(), "HmacSHA256")))
        val now = Instant.now()
        val claims = JwtClaimsSet.builder().subject("supervisor-1").issuedAt(now)
            .expiresAt(now.plus(1, ChronoUnit.HOURS)).claim("roles", roles).build()
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
    }

    private fun list(authorization: String?) = mockMvc.perform(
        get("/loan-requests?estado=PENDIENTE").apply {
            authorization?.let { header(HttpHeaders.AUTHORIZATION, it) }
        }
    )

    @Test
    fun `sin token o token vacio devuelve 401`() {
        listOf(null, "", "Bearer ", "Bearer null").forEach { list(it).andExpect(status().isUnauthorized) }
        // El 400 por estado inválido no se filtra antes de la autenticación
        mockMvc.perform(get("/loan-requests?estado=XYZ")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `registro de prestamo sin token o sin rol supervisor`() {
        fun post(authorization: String?) = mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/loans/register")
                .contentType("application/json").content("{}")
                .apply { authorization?.let { header(HttpHeaders.AUTHORIZATION, it) } }
        )
        listOf(null, "", "Bearer ").forEach { post(it).andExpect(status().isUnauthorized) }
        post("Bearer ${token(listOf("USER"))}").andExpect(status().isForbidden)
        // Con rol pasa la seguridad y llega a la validación del body
        post("Bearer ${token(listOf("SUPERVISOR"))}").andExpect(status().isBadRequest)
    }

    @Test
    fun `token sin rol supervisor 403 y con rol supervisor 200`() {
        list("Bearer ${token(listOf("USER"))}").andExpect(status().isForbidden)
        list("Bearer ${token(listOf("SUPERVISOR"))}").andExpect(status().isOk)
    }
}
