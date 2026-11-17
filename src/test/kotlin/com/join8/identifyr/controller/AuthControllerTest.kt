package com.join8.identifyr.controller

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.join8.identifyr.model.entity.Role
import com.join8.identifyr.model.entity.User
import com.join8.identifyr.repository.LoginAttemptRepository
import com.join8.identifyr.repository.UserRepository
import com.join8.identifyr.support.TestTokens
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Registro sin fotos, register-password, login con bloqueo, login de supervisor y auditoría. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension::class)
class AuthControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var loginAttemptRepository: LoginAttemptRepository
    @Autowired lateinit var jwtDecoder: JwtDecoder
    @Value("\${jwt.secret}") lateinit var jwtSecret: String

    @AfterEach
    fun cleanUp() {
        loginAttemptRepository.deleteAll()
        userRepository.deleteAll()
    }

    // --- helpers ---

    private fun postJson(path: String, json: String, authorization: String? = null): ResultActions = mockMvc.perform(
        post(path).contentType(MediaType.APPLICATION_JSON).content(json)
            .apply { authorization?.let { header(HttpHeaders.AUTHORIZATION, it) } }
    )

    private fun registerUser(phone: String) = postJson("/users/register", """{"phone":"$phone"}""")
    private fun registerPassword(userId: Any, password: String) =
        postJson("/auth/register-password", """{"userId":"$userId","password":"$password"}""")
    private fun login(phone: String, password: String) =
        postJson("/auth/login", """{"phone":"$phone","password":"$password"}""")

    private fun ResultActions.json(): JsonNode = objectMapper.readTree(andReturn().response.contentAsString)

    /** Usuario nuevo con contraseña, por el flujo real de la app. */
    private fun newUserWithPassword(phone: String = "987654321", password: String = "1234"): UUID {
        val id = UUID.fromString(registerUser(phone).andExpect(status().isCreated).json()["id"].asText())
        registerPassword(id, password).andExpect(status().isCreated)
        return id
    }

    // --- POST /users/register ---

    @Test
    fun `register sin fotos devuelve 201 con el contrato y deja las columnas en null`() {
        val body = registerUser("987654321")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.phone").value("987654321"))
            .andExpect(jsonPath("$.email").value(null as Any?))
            .andExpect(jsonPath("$.isVerified").value(false))
            .andExpect(jsonPath("$.message").value(null as Any?))
            .json()
        assertTrue(body.has("email") && body["email"].isNull, "email debe venir explícito en null")

        val user = userRepository.findById(UUID.fromString(body["id"].asText())).get()
        assertNull(user.photoFront)
        assertNull(user.photoBack)
        assertNull(user.passwordHash)
        assertEquals(Role.USER, user.role)
        assertEquals(0, user.failedLoginAttempts)
    }

    @Test
    fun `register con celular existente 409 y con formato invalido 400`() {
        registerUser("987654321").andExpect(status().isCreated)
        registerUser("987654321").andExpect(status().isConflict)
        listOf("98765432", "9876543210", "98765432a", "").forEach { registerUser(it).andExpect(status().isBadRequest) }
    }

    // --- POST /auth/register-password ---

    @Test
    fun `register-password con 4 digitos devuelve token valido con los claims y guarda BCrypt`() {
        val id = UUID.fromString(registerUser("987654321").json()["id"].asText())

        val body = registerPassword(id, "1234")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.userId").value(id.toString()))
            .json()

        val jwt = jwtDecoder.decode(body["token"].asText())
        assertEquals(id.toString(), jwt.subject)
        assertEquals("987654321", jwt.getClaimAsString("phone"))
        assertEquals("USER", jwt.getClaimAsString("role"))
        assertNotNull(jwt.id)
        assertEquals(Duration.ofHours(24), Duration.between(jwt.issuedAt, jwt.expiresAt))

        val hash = userRepository.findById(id).get().passwordHash!!
        assertTrue(hash.startsWith("\$2a\$10\$"), "debe ser BCrypt con costo 10")
        assertFalse(hash.contains("1234"))
    }

    @Test
    fun `register-password con formato invalido devuelve 400 sin repetir la contrasena`() {
        val id = UUID.fromString(registerUser("987654321").json()["id"].asText())
        listOf("123", "12345", "12a4", "abcd", " 1234", "").forEach { password ->
            val response = registerPassword(id, password)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andReturn().response.contentAsString
            if (password.isNotBlank()) assertFalse(response.contains(password.trim()), "la respuesta repite la contraseña")
        }
        registerPassword("no-es-uuid", "1234").andExpect(status().isBadRequest)
        assertNull(userRepository.findById(id).get().passwordHash)
    }

    @Test
    fun `register-password si ya tiene contrasena devuelve 409 y no la reemplaza`() {
        val id = newUserWithPassword(password = "1234")
        val hash = userRepository.findById(id).get().passwordHash

        registerPassword(id, "9999").andExpect(status().isConflict)

        assertEquals(hash, userRepository.findById(id).get().passwordHash)
        login("987654321", "1234").andExpect(status().isOk)
        login("987654321", "9999").andExpect(status().isUnauthorized)
    }

    @Test
    fun `register-password fuera de la ventana de 15 minutos devuelve 403 y usuario inexistente 404`() {
        val old = userRepository.save(User(phone = "911111111", createdAt = LocalDateTime.now().minusMinutes(16)))
        registerPassword(old.id!!, "1234")
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("PASSWORD_REGISTRATION_EXPIRED"))

        registerPassword(UUID.randomUUID(), "1234").andExpect(status().isNotFound)
    }

    // --- POST /auth/login ---

    @Test
    fun `login correcto devuelve token y userId`() {
        val id = newUserWithPassword()

        val body = login("987654321", "1234")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(id.toString()))
            .json()
        assertEquals(id.toString(), jwtDecoder.decode(body["token"].asText()).subject)
    }

    @Test
    fun `contrasena erronea y celular inexistente devuelven el mismo 401`() {
        newUserWithPassword()

        val wrong = login("987654321", "0000").andExpect(status().isUnauthorized).json()
        val unknown = login("900000000", "1234").andExpect(status().isUnauthorized).json()

        assertEquals("INVALID_CREDENTIALS", wrong["error"].asText())
        assertEquals(wrong["error"], unknown["error"])
        assertEquals(wrong["message"], unknown["message"])
    }

    @Test
    fun `usuario sin contrasena registrada recibe 401`() {
        registerUser("987654321").andExpect(status().isCreated)
        login("987654321", "1234").andExpect(status().isUnauthorized)
    }

    @Test
    fun `3 errores consecutivos bloquean con 423 aun con la contrasena correcta`() {
        val id = newUserWithPassword()

        login("987654321", "0000").andExpect(status().isUnauthorized)
        login("987654321", "1111").andExpect(status().isUnauthorized)
        login("987654321", "2222")
            .andExpect(status().isLocked)
            .andExpect(jsonPath("$.error").value("ACCOUNT_LOCKED"))
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))

        login("987654321", "1234").andExpect(status().isLocked)

        val user = userRepository.findById(id).get()
        assertEquals(3, user.failedLoginAttempts)
        val lockedFor = Duration.between(Instant.now(), user.lockedUntil)
        assertTrue(lockedFor > Duration.ofMinutes(14) && lockedFor <= Duration.ofMinutes(15))
    }

    @Test
    fun `vencido el bloqueo se puede volver a entrar`() {
        val id = newUserWithPassword()
        repeat(3) { login("987654321", "0000") }
        login("987654321", "1234").andExpect(status().isLocked)

        userRepository.save(userRepository.findById(id).get().apply { lockedUntil = Instant.now().minusSeconds(1) })

        login("987654321", "1234").andExpect(status().isOk)
        assertEquals(0, userRepository.findById(id).get().failedLoginAttempts)
        assertNull(userRepository.findById(id).get().lockedUntil)
    }

    @Test
    fun `tras un login exitoso el contador se reinicia`() {
        val id = newUserWithPassword()

        login("987654321", "0000").andExpect(status().isUnauthorized)
        login("987654321", "1111").andExpect(status().isUnauthorized)
        assertEquals(2, userRepository.findById(id).get().failedLoginAttempts)

        login("987654321", "1234").andExpect(status().isOk)
        assertEquals(0, userRepository.findById(id).get().failedLoginAttempts)

        // Dos fallos más no bloquean: el contador volvió a empezar
        login("987654321", "0000").andExpect(status().isUnauthorized)
        login("987654321", "1111").andExpect(status().isUnauthorized)
        login("987654321", "1234").andExpect(status().isOk)
    }

    @Test
    fun `login con formato invalido devuelve 400 y no cuenta como intento`() {
        val id = newUserWithPassword()
        login("987654321", "12345").andExpect(status().isBadRequest)
        login("98765432", "1234").andExpect(status().isBadRequest)
        assertEquals(0, userRepository.findById(id).get().failedLoginAttempts)
    }

    @Test
    fun `un token viejo en el header no bloquea los endpoints publicos`() {
        newUserWithPassword()
        val expired = "Bearer ${TestTokens.create(jwtSecret, UUID.randomUUID(), expiresAt = Instant.now().minusSeconds(3600))}"
        postJson("/auth/login", """{"phone":"987654321","password":"1234"}""", expired).andExpect(status().isOk)
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/users/phone/987654321")
                .header(HttpHeaders.AUTHORIZATION, "Bearer basura")
        ).andExpect(status().isOk)
    }

    // --- Auditoría y logs ---

    @Test
    fun `cada intento de login queda auditado sin la contrasena y los logs no la muestran`(output: CapturedOutput) {
        val id = newUserWithPassword(password = "4821")
        val token = login("987654321", "4821").json()["token"].asText()
        login("987654321", "7395").andExpect(status().isUnauthorized)
        login("900000000", "7395").andExpect(status().isUnauthorized)

        val attempts = loginAttemptRepository.findAllByPhoneOrderByCreatedAtAsc("987654321").filter { it.attemptType == "LOGIN" }
        assertEquals(listOf(true, false), attempts.map { it.isSuccessful })
        assertEquals(listOf(null, "BAD_PASSWORD"), attempts.map { it.failureReason })
        assertTrue(attempts.all { it.userId == id && it.ipAddress == "127.0.0.1" && it.createdAt != null })
        assertEquals("UNKNOWN_PHONE", loginAttemptRepository.findAllByPhoneOrderByCreatedAtAsc("900000000").single().failureReason)

        // Sin los UUID del log (podrían contener esos 4 dígitos por azar)
        val log = output.toString().replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"), "")
        listOf("4821", "7395", token, "987654321").forEach {
            assertFalse(log.contains(it), "el log contiene un dato sensible")
        }
    }

    // --- Login de supervisor ---

    @Test
    fun `login de supervisor solo emite token con role SUPERVISOR`() {
        val supervisorId = newUserWithPassword(phone = "955555555", password = "5555")
        userRepository.save(userRepository.findById(supervisorId).get().apply { role = Role.SUPERVISOR })
        newUserWithPassword(phone = "987654321", password = "1234")

        val token = postJson("/auth/supervisor/login", """{"phone":"955555555","password":"5555"}""")
            .andExpect(status().isOk).json()["token"].asText()
        assertEquals("SUPERVISOR", jwtDecoder.decode(token).getClaimAsString("role"))

        postJson("/auth/supervisor/login", """{"phone":"987654321","password":"1234"}""").andExpect(status().isUnauthorized)

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/loan-requests?estado=PENDIENTE")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        ).andExpect(status().isOk)
    }

    // --- OTP (siguiente fase) ---

    @Test
    fun `otp send y verify estan reservados y responden 501`() {
        postJson("/auth/otp/send", """{"phone":"987654321"}""").andExpect(status().isNotImplemented)
        postJson("/auth/otp/verify", """{"phone":"987654321","code":"123456"}""").andExpect(status().isNotImplemented)
    }
}
