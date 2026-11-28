package com.join8.identifyr.controller

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.join8.identifyr.model.entity.*
import com.join8.identifyr.repository.LoanRequestRepository
import com.join8.identifyr.repository.UserRepository
import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
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
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoanRequestListControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var loanRequestRepository: LoanRequestRepository
    @Value("\${jwt.secret}") lateinit var jwtSecret: String

    // Hace 2 días: dentro del rango por defecto (últimos 30 días)
    private val base = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)
    private lateinit var supervisorToken: String

    @BeforeEach
    fun setUp() {
        supervisorToken = token(listOf("SUPERVISOR"))
    }

    @AfterEach
    fun cleanUp() {
        loanRequestRepository.deleteAll()
        userRepository.deleteAll()
    }

    // --- helpers ---

    private fun token(
        roles: List<String>,
        expiresAt: Instant = Instant.now().plus(1, ChronoUnit.HOURS),
        secret: String = jwtSecret
    ): String {
        val encoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(SecretKeySpec(secret.toByteArray(), "HmacSHA256")))
        val claims = JwtClaimsSet.builder()
            .subject("supervisor-1")
            .issuedAt(expiresAt.minus(2, ChronoUnit.HOURS))
            .expiresAt(expiresAt)
            .claim("roles", roles)
            .build()
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
    }

    private fun list(query: String, bearer: String? = supervisorToken): ResultActions =
        listWithHeader(query, bearer?.let { "Bearer $it" })

    /** [authorization] es el valor crudo del header; null = no se envía. */
    private fun listWithHeader(query: String, authorization: String?): ResultActions {
        val request = get("/loan-requests?$query")
        authorization?.let { request.header(HttpHeaders.AUTHORIZATION, it) }
        return mockMvc.perform(request)
    }

    private fun json(actions: ResultActions): JsonNode =
        objectMapper.readTree(actions.andExpect(status().isOk).andReturn().response.contentAsString)

    private fun newUser(): UUID = userRepository.save(
        User(phone = "9${(10_000_000..99_999_999).random()}")
    ).id!!

    /** created/updated/processed: minutos desde [base]. */
    private fun loan(
        status: LoanRequestStatus,
        created: Long,
        updated: Long = created,
        processed: Long? = if (status == LoanRequestStatus.PROCESADA) updated else null,
        operationCode: String? = if (status == LoanRequestStatus.PROCESADA) "OP-${UUID.randomUUID()}" else null,
        phone: String = "987654321",
        amount: Int = 200,
        category: LoanCategory = LoanCategory.ORO,
        depositType: DepositType = DepositType.YAPE
    ): LoanRequest = loanRequestRepository.save(
        LoanRequest(
            userId = newUser(), phone = phone, depositType = depositType, category = category,
            score = 720, requestedAmount = amount, status = status, operationCode = operationCode,
            processedAt = processed?.let { base.plus(it, ChronoUnit.MINUTES) },
            createdAt = base.plus(created, ChronoUnit.MINUTES), updatedAt = base.plus(updated, ChronoUnit.MINUTES)
        )
    )

    private fun JsonNode.ids() = map { it["idSolicitud"].asText() }
    private fun ids(vararg r: LoanRequest) = r.map { it.id.toString() }

    /** Una solicitud de cada estado. */
    private fun mixed() {
        loan(LoanRequestStatus.PENDIENTE, created = 100)
        loan(LoanRequestStatus.PROCESADA, created = 100, updated = 200)
        loan(LoanRequestStatus.RECHAZADA, created = 100, updated = 200)
    }

    // --- casos obligatorios ---

    @Test
    fun `caso 1 - PENDIENTE devuelve solo pendientes de la mas antigua a la mas reciente`() {
        val p2 = loan(LoanRequestStatus.PENDIENTE, created = 20)
        val p1 = loan(LoanRequestStatus.PENDIENTE, created = 5)
        val p3 = loan(LoanRequestStatus.PENDIENTE, created = 40)
        loan(LoanRequestStatus.PROCESADA, created = 1, updated = 2)
        loan(LoanRequestStatus.RECHAZADA, created = 1, updated = 2)

        val body = json(list("estado=PENDIENTE"))
        assertTrue(body.isArray)
        assertEquals(ids(p1, p2, p3), body.ids())
        body.forEach {
            assertEquals("PENDIENTE", it["estado"].asText())
            assertTrue(it["codigoOperacion"].isNull)
        }
    }

    @Test
    fun `caso 2 - PROCESADA trae codigoOperacion y va de la ultima procesada a la primera`() {
        mixed()
        // updated_at en otro orden: se ordena por processed_at, no por updated_at
        val old = loan(LoanRequestStatus.PROCESADA, created = 0, updated = 900, processed = 10, operationCode = "OP-OLD")
        val newest = loan(LoanRequestStatus.PROCESADA, created = 0, updated = 1, processed = 500, operationCode = "OP-NEW")
        val middle = loan(LoanRequestStatus.PROCESADA, created = 0, updated = 2, processed = 300, operationCode = "OP-MID")

        val body = json(list("estado=PROCESADA"))
        assertEquals(4, body.size())
        assertEquals(ids(newest, middle), body.ids().take(2))
        assertEquals(old.id.toString(), body.ids().last())
        body.forEach {
            assertEquals("PROCESADA", it["estado"].asText())
            assertTrue(it["codigoOperacion"].isTextual && it["codigoOperacion"].asText().isNotBlank())
        }
        assertEquals("OP-NEW", body[0]["codigoOperacion"].asText())
    }

    @Test
    fun `caso 3 - RECHAZADA devuelve solo rechazadas, sin codigoOperacion, la mas reciente primero`() {
        mixed()
        val a = loan(LoanRequestStatus.RECHAZADA, created = 0, updated = 10)
        // Aunque tuviera un código guardado, en RECHAZADA se envía null
        val b = loan(LoanRequestStatus.RECHAZADA, created = 0, updated = 300, operationCode = "OP-X")

        val body = json(list("estado=RECHAZADA"))
        assertEquals(3, body.size())
        assertEquals(b.id.toString(), body.ids().first())
        assertEquals(a.id.toString(), body.ids().last())
        body.forEach {
            assertEquals("RECHAZADA", it["estado"].asText())
            assertTrue(it.has("codigoOperacion"), "la clave se envía aunque sea null")
            assertTrue(it["codigoOperacion"].isNull)
        }
    }

    @Test
    fun `caso 4 - estado sin distinguir mayusculas y con espacios`() {
        mixed()
        val expected = json(list("estado=PENDIENTE")).ids()
        assertEquals(1, expected.size)
        for (v in listOf("pendiente", "Pendiente", "pEnDiEnTe", " pendiente ", "  PENDIENTE	")) {
            val result = mockMvc.perform(get("/loan-requests").param("estado", v)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $supervisorToken"))
            assertEquals(expected, json(result).ids(), "'$v'")
        }
        assertEquals(1, json(list("estado=procesada")).size())
        assertEquals(1, json(list("estado=rechazada")).size())
    }

    @Test
    fun `caso 5 - sin estado o con otro valor devuelve 400 con mensaje claro`() {
        list("x=1").andExpect(status().isBadRequest)
        mockMvc.perform(get("/loan-requests").param("estado", "   ").header(HttpHeaders.AUTHORIZATION, "Bearer $supervisorToken"))
            .andExpect(status().isBadRequest)
        for (q in listOf("", "estado=", "estado=OTRO", "estado=PENDING", "estado=1", "estado=TODAS")) {
            list(q).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("estado debe ser PENDIENTE, PROCESADA o RECHAZADA"))
        }
    }

    @Test
    fun `caso 6 - sin resultados devuelve 200 con arreglo vacio`() {
        loan(LoanRequestStatus.PENDIENTE, created = 0)
        for (estado in listOf("PROCESADA", "RECHAZADA")) {
            val raw = list("estado=$estado").andExpect(status().isOk).andReturn().response.contentAsString
            assertEquals("[]", raw.filterNot { it.isWhitespace() })
        }
    }

    @Test
    fun `caso 8 - montoSolicitado es entero y las claves son exactamente las del contrato`() {
        val saved = loan(LoanRequestStatus.PROCESADA, created = 0, operationCode = "OP-9", phone = "912345678",
            amount = 150, category = LoanCategory.PLATA, depositType = DepositType.PLIN)

        val raw = list("estado=PROCESADA").andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(Regex("\"montoSolicitado\"\\s*:\\s*150\\s*[,}\\n]").containsMatchIn(raw), raw)

        val item = objectMapper.readTree(raw).single()
        assertEquals(
            setOf("idSolicitud", "numCelular", "montoSolicitado", "puntaje", "categoria", "estado",
                "codigoOperacion", "carteraDigital"),
            item.fieldNames().asSequence().toSet()
        )
        assertTrue(item["montoSolicitado"].isInt)
        assertTrue(item["puntaje"].isInt)
        assertTrue(item["idSolicitud"].isTextual)
        assertTrue(item["numCelular"].isTextual)
        assertEquals(saved.id.toString(), item["idSolicitud"].asText())
        assertEquals("912345678", item["numCelular"].asText())
        assertEquals(150, item["montoSolicitado"].asInt())
        assertEquals(720, item["puntaje"].asInt())
        assertEquals("PLATA", item["categoria"].asText())
        assertEquals("PROCESADA", item["estado"].asText())
        assertEquals("OP-9", item["codigoOperacion"].asText())
        assertEquals("PLIN", item["carteraDigital"].asText())
    }

    // --- seguridad ---

    @Test
    fun `sin token o con token vacio devuelve 401`() {
        loan(LoanRequestStatus.PENDIENTE, created = 0)

        listOf(null, "", "Bearer", "Bearer ", "Bearer    ", "bearer", "Bearer null").forEach { header ->
            listWithHeader("estado=PENDIENTE", header)
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
        }
        // El 400 por estado inválido no se filtra antes de la autenticación
        listWithHeader("estado=XYZ", null).andExpect(status().isUnauthorized)
    }

    @Test
    fun `con token presente se valida - invalido 401 y sin rol supervisor 403`() {
        loan(LoanRequestStatus.PENDIENTE, created = 0)

        list("estado=PENDIENTE", bearer = "no-es-un-jwt").andExpect(status().isUnauthorized)
        list("estado=PENDIENTE", bearer = token(listOf("SUPERVISOR"), secret = "x".repeat(64)))
            .andExpect(status().isUnauthorized)                                  // firma con otra clave
        list("estado=PENDIENTE", bearer = token(listOf("SUPERVISOR"), expiresAt = Instant.now().minusSeconds(3600)))
            .andExpect(status().isUnauthorized)                                  // vencido
        list("estado=PENDIENTE", bearer = token(listOf("USER"))).andExpect(status().isForbidden)
        list("estado=PENDIENTE", bearer = token(emptyList())).andExpect(status().isForbidden)
        list("estado=PENDIENTE").andExpect(status().isOk)
    }

    @Test
    fun `el registro de solicitudes lo hace el cliente con su token, sin rol supervisor`() {
        val userId = newUser()
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/loan-requests/register")
                .contentType("application/json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${com.join8.identifyr.support.TestTokens.user(jwtSecret, userId, "987654321")}")
                .content("""{"idUsuario":"$userId","numCelular":"987654321","tipoDeposito":"YAPE",
                    "categoria":"ORO","puntaje":720,"montoSolicitado":200}""")
        ).andExpect(status().isCreated)
    }
}
