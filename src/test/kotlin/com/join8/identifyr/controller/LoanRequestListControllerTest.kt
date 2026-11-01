package com.join8.identifyr.controller

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.join8.identifyr.model.entity.*
import com.join8.identifyr.repository.LoanRequestRepository
import com.join8.identifyr.repository.UserRepository
import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.hamcrest.Matchers.containsString
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoanRequestListControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var loanRequestRepository: LoanRequestRepository
    @Value("\${jwt.secret}") lateinit var jwtSecret: String

    private val base = Instant.parse("2026-11-01T10:00:00Z")
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
        User(phone = "9${(10_000_000..99_999_999).random()}", email = "", photoFront = UUID.randomUUID().toString(),
            photoBack = UUID.randomUUID().toString(), passwordHash = "")
    ).id!!

    /** created/updated: minutos desde [base]. */
    private fun loan(
        status: LoanRequestStatus,
        created: Long,
        updated: Long = created,
        operationCode: String? = null,
        phone: String = "987654321",
        amount: Int = 200
    ): LoanRequest = loanRequestRepository.save(
        LoanRequest(
            userId = newUser(), phone = phone, depositType = DepositType.YAPE, category = LoanCategory.ORO,
            score = 720, requestedAmount = amount, status = status, operationCode = operationCode,
            createdAt = base.plus(created, ChronoUnit.MINUTES), updatedAt = base.plus(updated, ChronoUnit.MINUTES)
        )
    )

    private fun JsonNode.ids() = this["items"].map { it["id"].asText() }

    // --- tests ---

    @Test
    fun `PENDIENTE devuelve solo pendientes de la mas antigua a la mas reciente`() {
        val p2 = loan(LoanRequestStatus.PENDIENTE, created = 20)
        val p1 = loan(LoanRequestStatus.PENDIENTE, created = 5)
        val p3 = loan(LoanRequestStatus.PENDIENTE, created = 30, updated = 31)
        loan(LoanRequestStatus.PROCESADA, created = 1, updated = 40, operationCode = "OP-1")
        loan(LoanRequestStatus.RECHAZADA, created = 2, updated = 41)

        val body = json(list("estado=PENDIENTE"))
        assertEquals(listOf(p1, p2, p3).map { it.id.toString() }, body.ids())
        body["items"].forEach { assertEquals("PENDIENTE", it["estado"].asText()) }
    }

    @Test
    fun `PROCESADA trae codigoOperacion y va de la mas reciente a la mas antigua por updated_at`() {
        // created y updated en orden distinto: debe mandar updated_at
        val old = loan(LoanRequestStatus.PROCESADA, created = 50, updated = 60, operationCode = "OP-OLD")
        val newest = loan(LoanRequestStatus.PROCESADA, created = 1, updated = 90, operationCode = "OP-NEW")
        val middle = loan(LoanRequestStatus.PROCESADA, created = 10, updated = 70, operationCode = "OP-MID")
        loan(LoanRequestStatus.PENDIENTE, created = 0)

        val body = json(list("estado=PROCESADA"))
        assertEquals(listOf(newest, middle, old).map { it.id.toString() }, body.ids())
        assertEquals(listOf("OP-NEW", "OP-MID", "OP-OLD"), body["items"].map { it["codigoOperacion"].asText() })
    }

    @Test
    fun `RECHAZADA va de la mas reciente a la mas antigua por updated_at`() {
        val a = loan(LoanRequestStatus.RECHAZADA, created = 0, updated = 10)
        val b = loan(LoanRequestStatus.RECHAZADA, created = 5, updated = 20)
        assertEquals(listOf(b, a).map { it.id.toString() }, json(list("estado=RECHAZADA")).ids())
    }

    @Test
    fun `codigoOperacion es null en PENDIENTE y RECHAZADA y la clave se envia`() {
        loan(LoanRequestStatus.PENDIENTE, created = 0)
        loan(LoanRequestStatus.RECHAZADA, created = 1, updated = 2)

        listOf("PENDIENTE", "RECHAZADA").forEach { estado ->
            val item = json(list("estado=$estado"))["items"].single()
            assertEquals(true, item.has("codigoOperacion"), "falta la clave codigoOperacion en $estado")
            assertEquals(true, item["codigoOperacion"].isNull)
        }
    }

    @Test
    fun `conteos trae los tres estados aunque alguno sea 0`() {
        repeat(3) { loan(LoanRequestStatus.PENDIENTE, created = it.toLong()) }
        loan(LoanRequestStatus.PROCESADA, created = 10, operationCode = "OP-1")

        // El conteo es global: igual sin importar la pestaña consultada
        listOf("PENDIENTE", "PROCESADA", "RECHAZADA").forEach { estado ->
            list("estado=$estado")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.conteos.PENDIENTE").value(3))
                .andExpect(jsonPath("$.conteos.PROCESADA").value(1))
                .andExpect(jsonPath("$.conteos.RECHAZADA").value(0))
        }
    }

    @Test
    fun `sin resultados devuelve 200 con items vacio`() {
        list("estado=RECHAZADA")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items", hasSize<Any>(0)))
            .andExpect(jsonPath("$.totalItems").value(0))
            .andExpect(jsonPath("$.totalPages").value(0))
            .andExpect(jsonPath("$.conteos.PENDIENTE").value(0))
            .andExpect(jsonPath("$.conteos.PROCESADA").value(0))
            .andExpect(jsonPath("$.conteos.RECHAZADA").value(0))
    }

    @Test
    fun `estado faltante o invalido y page o size fuera de rango devuelven 400`() {
        list("").andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message", containsString("estado es obligatorio")))
        list("estado=").andExpect(status().isBadRequest)
        list("estado=pendiente").andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message", containsString("estado inválido")))
        list("estado=APROBADA").andExpect(status().isBadRequest)
        list("estado=PENDIENTE&size=0").andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message", containsString("size debe estar entre 1 y 100")))
        list("estado=PENDIENTE&size=101").andExpect(status().isBadRequest)
        list("estado=PENDIENTE&page=-1").andExpect(status().isBadRequest)
        list("estado=PENDIENTE&page=abc").andExpect(status().isBadRequest)
        list("estado=PENDIENTE&size=1&page=0").andExpect(status().isOk)
        list("estado=PENDIENTE&size=100").andExpect(status().isOk)
    }

    @Test
    fun `paginacion coherente`() {
        val all = (0L until 5L).map { loan(LoanRequestStatus.PENDIENTE, created = it) }

        val defaults = json(list("estado=PENDIENTE"))
        assertEquals(0, defaults["page"].asInt())
        assertEquals(20, defaults["size"].asInt())
        assertEquals(5, defaults["items"].size())

        val pages = (0..2).map { json(list("estado=PENDIENTE&page=$it&size=2")) }
        pages.forEachIndexed { i, page ->
            assertEquals(i, page["page"].asInt())
            assertEquals(2, page["size"].asInt())
            assertEquals(5, page["totalItems"].asLong())
            assertEquals(3, page["totalPages"].asInt())
        }
        assertEquals(listOf(2, 2, 1), pages.map { it["items"].size() })
        assertEquals(all.map { it.id.toString() }, pages.flatMap { it.ids() })

        // Página más allá del final: 200 vacío con los mismos totales
        val beyond = json(list("estado=PENDIENTE&page=3&size=2"))
        assertEquals(0, beyond["items"].size())
        assertEquals(5, beyond["totalItems"].asLong())
        assertEquals(3, beyond["totalPages"].asInt())
    }

    @Test
    fun `sin token o con token vacio entrega el listado (temporal, allow-anonymous=true)`() {
        loan(LoanRequestStatus.PENDIENTE, created = 0)

        listOf(null, "", "Bearer", "Bearer ", "Bearer    ", "bearer", "Bearer null").forEach { header ->
            listWithHeader("estado=PENDIENTE", header)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items", hasSize<Any>(1)))
                .andExpect(jsonPath("$.conteos.PENDIENTE").value(1))
        }
        // Las validaciones siguen aplicando sin token
        listWithHeader("estado=XYZ", null).andExpect(status().isBadRequest)
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
    fun `el registro de solicitudes sigue siendo publico`() {
        val userId = newUser()
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/loan-requests/register")
                .contentType("application/json")
                .content("""{"idUsuario":"$userId","numCelular":"987654321","tipoDeposito":"YAPE",
                    "categoria":"ORO","puntaje":720,"montoSolicitado":200}""")
        ).andExpect(status().isCreated)
    }

    @Test
    fun `las claves JSON de la respuesta son exactamente las del ejemplo`() {
        val saved = loan(LoanRequestStatus.PROCESADA, created = 0, updated = 5, operationCode = "OP-9", phone = "912345678", amount = 150)

        val body = json(list("estado=PROCESADA&page=0&size=20"))
        assertEquals(
            setOf("items", "page", "size", "totalItems", "totalPages", "conteos"),
            body.fieldNames().asSequence().toSet()
        )
        assertEquals(listOf("PENDIENTE", "PROCESADA", "RECHAZADA"), body["conteos"].fieldNames().asSequence().toList())

        val item = body["items"].single()
        assertEquals(
            setOf("id", "idUsuario", "numCelular", "montoSolicitado", "puntaje", "categoria", "tipoDeposito",
                "estado", "codigoOperacion", "fechaRegistro", "fechaActualizacion"),
            item.fieldNames().asSequence().toSet()
        )
        assertEquals(saved.id.toString(), item["id"].asText())
        assertEquals(saved.userId.toString(), item["idUsuario"].asText())
        assertEquals("912345678", item["numCelular"].asText())
        assertEquals(150, item["montoSolicitado"].asInt())
        assertEquals(720, item["puntaje"].asInt())
        assertEquals("ORO", item["categoria"].asText())
        assertEquals("YAPE", item["tipoDeposito"].asText())
        assertEquals("PROCESADA", item["estado"].asText())
        assertEquals("OP-9", item["codigoOperacion"].asText())
        // ISO-8601 en UTC
        assertEquals("2026-11-01T10:00:00Z", item["fechaRegistro"].asText())
        assertEquals("2026-11-01T10:05:00Z", item["fechaActualizacion"].asText())
    }
}
