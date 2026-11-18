package com.join8.identifyr.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.join8.identifyr.model.entity.LoginAttempt
import com.join8.identifyr.model.entity.StaffUser
import com.join8.identifyr.repository.LoginAttemptRepository
import com.join8.identifyr.repository.StaffUserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** POST /auth/staff/login: usuario + contraseña fuerte de staff_users, bloqueo, auditoría y acceso a /loan-requests. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffAuthControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var staffUserRepository: StaffUserRepository
    @Autowired lateinit var loginAttemptRepository: LoginAttemptRepository
    @Autowired lateinit var passwordEncoder: BCryptPasswordEncoder
    @Autowired lateinit var jwtDecoder: JwtDecoder

    private lateinit var supervisorId: UUID

    @BeforeEach
    fun setUp() {
        supervisorId = staffUserRepository.save(
            StaffUser(username = "supervisor", passwordHash = passwordEncoder.encode(PASSWORD))
        ).id!!
    }

    @AfterEach
    fun cleanUp() {
        loginAttemptRepository.deleteAll()
        staffUserRepository.deleteAll()
    }

    private fun login(username: String, password: String): ResultActions = mockMvc.perform(
        post("/auth/staff/login").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("username" to username, "password" to password)))
    )

    private fun token(result: ResultActions): String =
        objectMapper.readTree(result.andReturn().response.contentAsString)["token"].asText()

    private fun staffAttempts() = loginAttemptRepository.findAll().filter { it.attemptType == LoginAttempt.TYPE_STAFF_LOGIN }

    @Test
    fun `login correcto emite token SUPERVISOR sin phone que permite ver solicitudes`() {
        val result = login("supervisor", PASSWORD)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(supervisorId.toString()))

        val token = token(result)
        val jwt = jwtDecoder.decode(token)
        assertEquals(supervisorId.toString(), jwt.subject)
        assertEquals("SUPERVISOR", jwt.getClaimAsString("role"))
        assertNull(jwt.getClaimAsString("phone"))

        mockMvc.perform(get("/loan-requests?estado=PENDIENTE").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)

        val attempt = staffAttempts().single()
        assertTrue(attempt.isSuccessful)
        assertEquals(supervisorId, attempt.staffUserId)
        assertNull(attempt.userId)
    }

    @Test
    fun `el usuario no distingue mayusculas ni espacios alrededor`() {
        login("  SuperVisor ", PASSWORD).andExpect(status().isOk)
    }

    @Test
    fun `usuario inexistente, deshabilitado o clave erronea devuelven el mismo 401`() {
        login("nadie", PASSWORD)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.message").value("Usuario o contraseña incorrectos"))
        login("supervisor", "otra-clave").andExpect(status().isUnauthorized)

        staffUserRepository.save(staffUserRepository.findById(supervisorId).get().apply { enabled = false })
        login("supervisor", PASSWORD).andExpect(status().isUnauthorized)

        assertEquals(
            listOf("UNKNOWN_USERNAME", "BAD_PASSWORD", "DISABLED"),
            staffAttempts().sortedBy { it.createdAt }.map { it.failureReason }
        )
    }

    @Test
    fun `tres fallos bloquean la cuenta con 423 aun con la clave correcta`() {
        repeat(2) { login("supervisor", "mala").andExpect(status().isUnauthorized) }
        login("supervisor", "mala")
            .andExpect(status().isLocked)
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
        login("supervisor", PASSWORD).andExpect(status().isLocked)

        assertNotNull(staffUserRepository.findById(supervisorId).get().lockedUntil)
    }

    @Test
    fun `un acierto reinicia el contador de fallos`() {
        repeat(2) { login("supervisor", "mala").andExpect(status().isUnauthorized) }
        login("supervisor", PASSWORD).andExpect(status().isOk)
        assertEquals(0, staffUserRepository.findById(supervisorId).get().failedLoginAttempts)
    }

    @Test
    fun `body invalido devuelve 400`() {
        login("", PASSWORD).andExpect(status().isBadRequest)
        login("supervisor", "").andExpect(status().isBadRequest)
        login("supervisor", "x".repeat(73)).andExpect(status().isBadRequest)
    }

    @Test
    fun `un token viejo en el header no bloquea el login`() {
        mockMvc.perform(
            post("/auth/staff/login").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-vencido")
                .content("""{"username":"supervisor","password":"$PASSWORD"}""")
        ).andExpect(status().isOk)
    }

    private companion object {
        const val PASSWORD = "Clave-Segura-2026"
    }
}
