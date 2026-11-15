package com.join8.identifyr.controller

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.join8.identifyr.model.entity.*
import com.join8.identifyr.repository.LoanInstallmentRepository
import com.join8.identifyr.repository.LoanRepository
import com.join8.identifyr.repository.LoanRequestRepository
import com.join8.identifyr.repository.UserRepository
import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Flujo completo de POST /loans/register (y su efecto en GET /loan-requests y GET /loans/current/phone). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoanControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var loanRequestRepository: LoanRequestRepository
    @Autowired lateinit var loanRepository: LoanRepository
    @Autowired lateinit var installmentRepository: LoanInstallmentRepository
    @MockitoBean lateinit var clock: Clock
    @Value("\${jwt.secret}") lateinit var jwtSecret: String

    // 2026-11-14 10:32:05 en Lima (UTC-5)
    private val defaultNow = Instant.parse("2026-11-14T15:32:05Z")
    private lateinit var supervisorToken: String
    private lateinit var pending: LoanRequest

    @BeforeEach
    fun setUp() {
        `when`(clock.instant()).thenReturn(defaultNow)
        `when`(clock.millis()).thenReturn(defaultNow.toEpochMilli())
        supervisorToken = token(listOf("SUPERVISOR"))
        pending = loanRequest()
    }

    @AfterEach
    fun cleanUp() {
        installmentRepository.deleteAll()
        loanRepository.deleteAll()
        loanRequestRepository.deleteAll()
        userRepository.deleteAll()
    }

    // --- helpers ---

    private fun token(roles: List<String>, subject: String = "supervisor-1"): String {
        val encoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(SecretKeySpec(jwtSecret.toByteArray(), "HmacSHA256")))
        val now = Instant.now()
        val claims = JwtClaimsSet.builder().subject(subject).issuedAt(now)
            .expiresAt(now.plus(1, ChronoUnit.HOURS)).claim("roles", roles).build()
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
    }

    private fun loanRequest(
        status: LoanRequestStatus = LoanRequestStatus.PENDIENTE,
        amount: Int = 200,
        frequency: Int? = 15,
        phone: String = "987654321",
        createdAt: Instant = Instant.parse("2026-11-10T14:00:00Z")
    ): LoanRequest {
        val userId = userRepository.save(
            User(phone = "9${(10_000_000..99_999_999).random()}", email = "", photoFront = UUID.randomUUID().toString(),
                photoBack = UUID.randomUUID().toString(), passwordHash = "")
        ).id!!
        return loanRequestRepository.save(
            LoanRequest(userId = userId, phone = phone, depositType = DepositType.PLIN, category = LoanCategory.ORO,
                score = 72, requestedAmount = amount, installmentFrequency = frequency, status = status,
                createdAt = createdAt)
        )
    }

    private fun body(idSolicitud: Any? = pending.id, codigoOperacion: Any? = "OP-458712"): String {
        fun json(v: Any?) = if (v == null) "null" else "\"$v\""
        return """{"idSolicitud":${json(idSolicitud)},"codigoOperacion":${json(codigoOperacion)}}"""
    }

    private fun register(json: String, authorization: String? = "Bearer $supervisorToken"): ResultActions {
        val request = post("/loans/register").contentType(MediaType.APPLICATION_JSON).content(json)
        authorization?.let { request.header(HttpHeaders.AUTHORIZATION, it) }
        return mockMvc.perform(request)
    }

    // BigDecimal para comprobar también que el JSON trae 2 decimales (200.00, no 200.0 ni 200)
    private fun ResultActions.json(): JsonNode = objectMapper.copy()
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true))
        .readTree(andReturn().response.contentAsString)

    private fun JsonNode.keys() = fieldNames().asSequence().toSet()

    private fun assertNothingSaved(request: LoanRequest = pending) {
        val reloaded = loanRequestRepository.findById(request.id!!).get()
        assertEquals(request.status, reloaded.status)
        assertNull(reloaded.operationCode)
        assertNull(reloaded.processedAt)
        assertNull(loanRepository.findByLoanRequestId(request.id!!))
    }

    // --- 201 ---

    @Test
    fun `caso 1 - 200 soles quincenal crea prestamo, 4 cuotas y procesa la solicitud`() {
        val res = register(body()).andExpect(status().isCreated).json()

        assertEquals(
            setOf("idPrestamo", "idSolicitud", "codigoOperacion", "fechaRegistroOperacion", "fechaDesembolso",
                "montoDesembolsado", "tasaInteres", "montoInteres", "montoTotal", "frecuenciaCuota", "numeroCuotas",
                "plazoDias", "cuotas"),
            res.keys()
        )
        assertEquals(pending.id.toString(), res["idSolicitud"].asText())
        assertEquals("OP-458712", res["codigoOperacion"].asText())
        assertEquals("2026-11-14T10:32:05-05:00", res["fechaRegistroOperacion"].asText())
        assertEquals("2026-11-14", res["fechaDesembolso"].asText())
        assertEquals(BigDecimal("200.00"), res["montoDesembolsado"].decimalValue())
        assertEquals(BigDecimal("18.00"), res["tasaInteres"].decimalValue())
        assertEquals(BigDecimal("36.00"), res["montoInteres"].decimalValue())
        assertEquals(BigDecimal("236.00"), res["montoTotal"].decimalValue())
        assertEquals(15, res["frecuenciaCuota"].asInt())
        assertEquals(4, res["numeroCuotas"].asInt())
        assertEquals(61, res["plazoDias"].asInt())
        assertEquals(
            listOf(
                Triple(1, "2026-11-29", "59.00"), Triple(2, "2026-12-14", "59.00"),
                Triple(3, "2026-12-29", "59.00"), Triple(4, "2027-01-14", "59.00")
            ),
            res["cuotas"].map { Triple(it["numeroCuota"].asInt(), it["fechaVencimiento"].asText(), it["montoCuota"].decimalValue().toPlainString()) }
        )
        res["cuotas"].forEach {
            assertEquals(setOf("numeroCuota", "fechaVencimiento", "montoCuota", "estado"), it.keys())
            assertEquals("PENDIENTE", it["estado"].asText())
        }

        // BD
        val loan = loanRepository.findById(UUID.fromString(res["idPrestamo"].asText())).get()
        assertEquals(pending.id, loan.loanRequestId)
        assertEquals(pending.userId, loan.userId)
        assertEquals("987654321", loan.phone)
        assertEquals(DepositType.PLIN, loan.depositType)
        assertEquals(LoanCategory.ORO, loan.category)
        assertEquals(72, loan.score)
        assertEquals(0, BigDecimal("0.18").compareTo(loan.interestRate))
        assertEquals(0, BigDecimal("236").compareTo(loan.outstandingBalance))
        assertEquals(LoanStatus.VIGENTE, loan.status)
        assertEquals(defaultNow, loan.operationRegisteredAt)
        assertEquals(LocalDate.parse("2026-11-14"), loan.disbursementDate)
        assertEquals(pending.createdAt, loan.requestDate)
        assertEquals(LocalDate.parse("2027-01-14"), loan.paymentDate)
        assertEquals("supervisor-1", loan.createdBy)

        val installments = installmentRepository.findAllByLoanIdOrderByInstallmentNumberAsc(loan.id!!)
        assertEquals(4, installments.size)
        installments.forEach {
            assertEquals(0, BigDecimal.ZERO.compareTo(it.paidAmount))
            assertNull(it.paidAt)
            assertEquals(InstallmentStatus.PENDIENTE, it.status)
        }

        val processed = loanRequestRepository.findById(pending.id!!).get()
        assertEquals(LoanRequestStatus.PROCESADA, processed.status)
        assertEquals("OP-458712", processed.operationCode)
        assertEquals(defaultNow, processed.processedAt)
    }

    @Test
    fun `caso 2 - 200 soles mensual`() {
        val monthly = loanRequest(frequency = 30)
        val res = register(body(idSolicitud = monthly.id)).andExpect(status().isCreated).json()

        assertEquals(BigDecimal("21.00"), res["tasaInteres"].decimalValue())
        assertEquals(BigDecimal("42.00"), res["montoInteres"].decimalValue())
        assertEquals(BigDecimal("242.00"), res["montoTotal"].decimalValue())
        assertEquals(2, res["numeroCuotas"].asInt())
        assertEquals(listOf("2026-12-14", "2027-01-14"), res["cuotas"].map { it["fechaVencimiento"].asText() })
        assertEquals(listOf("121.00", "121.00"), res["cuotas"].map { it["montoCuota"].decimalValue().toPlainString() })
    }

    @Test
    fun `caso 3 - 51 soles, el sobrante va a la ultima cuota`() {
        val small = loanRequest(amount = 51)
        val res = register(body(idSolicitud = small.id)).andExpect(status().isCreated).json()

        assertEquals(BigDecimal("60.18"), res["montoTotal"].decimalValue())
        assertEquals(listOf("15.04", "15.04", "15.04", "15.06"), res["cuotas"].map { it["montoCuota"].decimalValue().toPlainString() })
    }

    @Test
    fun `caso 4 - solicitud del 1 y operacion del 14, el cronograma parte del 14`() {
        val old = loanRequest(createdAt = Instant.parse("2026-11-01T15:00:00Z"))
        val res = register(body(idSolicitud = old.id)).andExpect(status().isCreated).json()

        assertEquals("2026-11-14", res["fechaDesembolso"].asText())
        assertEquals("2026-11-29", res["cuotas"][0]["fechaVencimiento"].asText())
        // La fecha de la solicitud solo queda como dato histórico
        assertEquals(Instant.parse("2026-11-01T15:00:00Z"), loanRepository.findByLoanRequestId(old.id!!)!!.requestDate)
    }

    @Test
    fun `caso 5 - a las 2330 de Lima (0430 UTC del dia siguiente) se usa la fecha de Lima`() {
        `when`(clock.instant()).thenReturn(Instant.parse("2026-11-15T04:30:00Z"))
        val res = register(body()).andExpect(status().isCreated).json()

        assertEquals("2026-11-14T23:30:00-05:00", res["fechaRegistroOperacion"].asText())
        assertEquals("2026-11-14", res["fechaDesembolso"].asText())
        assertEquals("2027-01-14", res["cuotas"].last()["fechaVencimiento"].asText())
    }

    @Test
    fun `solo se usan idSolicitud y codigoOperacion - montos y fechas del cliente se ignoran`() {
        val res = register(
            """{"idSolicitud":"${pending.id}","codigoOperacion":"OP-1","monto":99999,"montoDesembolsado":99999,
                "tasaInteres":0,"frecuenciaCuota":30,"fechaDesembolso":"2020-01-01","fechaRegistroOperacion":"2020-01-01T00:00:00Z"}"""
        ).andExpect(status().isCreated).json()

        assertEquals(BigDecimal("200.00"), res["montoDesembolsado"].decimalValue())
        assertEquals(15, res["frecuenciaCuota"].asInt())
        assertEquals("2026-11-14", res["fechaDesembolso"].asText())
    }

    @Test
    fun `codigoOperacion se guarda sin espacios alrededor`() {
        register(body(codigoOperacion = "  OP-77  ")).andExpect(status().isCreated)
            .andExpect(jsonPath("$.codigoOperacion").value("OP-77"))
        assertEquals("OP-77", loanRepository.findByLoanRequestId(pending.id!!)!!.operationCode)
        assertEquals("OP-77", loanRequestRepository.findById(pending.id!!).get().operationCode)
    }

    // --- 400 ---

    @Test
    fun `idSolicitud o codigoOperacion vacios o invalidos devuelven 400`() {
        register(body(idSolicitud = null)).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message", containsString("idSolicitud")))
        register(body(idSolicitud = "")).andExpect(status().isBadRequest)
        register(body(idSolicitud = "no-es-uuid")).andExpect(status().isBadRequest)
        register(body(codigoOperacion = null)).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message", containsString("codigoOperacion")))
        register(body(codigoOperacion = "   ")).andExpect(status().isBadRequest)
        register(body(codigoOperacion = "X".repeat(51))).andExpect(status().isBadRequest)
        register("""{"idSolicitud":"${pending.id}"}""").andExpect(status().isBadRequest)
        register("no es json").andExpect(status().isBadRequest)
        assertNothingSaved()
    }

    // --- 404 ---

    @Test
    fun `solicitud inexistente devuelve 404`() {
        register(body(idSolicitud = UUID.randomUUID())).andExpect(status().isNotFound)
        assertEquals(0, loanRepository.count())
    }

    // --- 409 ---

    @Test
    fun `solicitud ya PROCESADA o RECHAZADA devuelve 409`() {
        for (s in listOf(LoanRequestStatus.PROCESADA, LoanRequestStatus.RECHAZADA)) {
            val req = loanRequest(status = s)
            register(body(idSolicitud = req.id, codigoOperacion = "OP-$s")).andExpect(status().isConflict)
                .andExpect(jsonPath("$.message", containsString(s.name)))
        }
        assertEquals(0, loanRepository.count())
    }

    @Test
    fun `segundo registro de la misma solicitud devuelve 409 con el idPrestamo existente`() {
        val first = register(body()).andExpect(status().isCreated).json()
        register(body(codigoOperacion = "OP-OTRO")).andExpect(status().isConflict)
            .andExpect(jsonPath("$.idPrestamo").value(first["idPrestamo"].asText()))
        assertEquals(1, loanRepository.count())
        assertEquals(4, installmentRepository.count())
    }

    @Test
    fun `codigoOperacion repetido devuelve 409 y la segunda solicitud sigue PENDIENTE`() {
        register(body(codigoOperacion = "OP-DUP")).andExpect(status().isCreated)
        val other = loanRequest()
        register(body(idSolicitud = other.id, codigoOperacion = " OP-DUP ")).andExpect(status().isConflict)
            .andExpect(jsonPath("$.message", containsString("OP-DUP")))
        assertNothingSaved(other)
        assertEquals(1, loanRepository.count())
    }

    // --- 422 ---

    @Test
    fun `frecuencia distinta de 15 o 30, sin frecuencia o monto no positivo devuelven 422 sin guardar nada`() {
        for ((i, req) in listOf(loanRequest(frequency = 7), loanRequest(frequency = null), loanRequest(amount = 0)).withIndex()) {
            register(body(idSolicitud = req.id, codigoOperacion = "OP-422-$i")).andExpect(status().isUnprocessableEntity)
            assertNothingSaved(req)
        }
        assertEquals(0, installmentRepository.count())
    }

    // --- seguridad ---

    @Test
    fun `con token invalido 401 y sin rol supervisor 403`() {
        register(body(), authorization = "Bearer no-es-un-jwt").andExpect(status().isUnauthorized)
        register(body(), authorization = "Bearer ${token(listOf("USER"))}").andExpect(status().isForbidden)
        assertNothingSaved()
    }

    @Test
    fun `sin token se permite mientras allow-anonymous=true y created_by queda null`() {
        register(body(), authorization = null).andExpect(status().isCreated)
        assertNull(loanRepository.findByLoanRequestId(pending.id!!)!!.createdBy)
    }

    // --- endpoints relacionados ---

    @Test
    fun `caso 7 del listado - tras registrar, la solicitud sale de PENDIENTE y aparece en PROCESADA con su codigo`() {
        mockMvc.perform(get("/loan-requests?estado=PENDIENTE"))
            .andExpect(jsonPath("$[0].idSolicitud").value(pending.id.toString()))

        register(body(codigoOperacion = "OP-FLUJO")).andExpect(status().isCreated)

        mockMvc.perform(get("/loan-requests?estado=PENDIENTE"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
        mockMvc.perform(get("/loan-requests?estado=PROCESADA"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].idSolicitud").value(pending.id.toString()))
            .andExpect(jsonPath("$[0].numCelular").value("987654321"))
            .andExpect(jsonPath("$[0].montoSolicitado").value(200))
            .andExpect(jsonPath("$[0].puntaje").value(72))
            .andExpect(jsonPath("$[0].categoria").value("ORO"))
            .andExpect(jsonPath("$[0].estado").value("PROCESADA"))
            .andExpect(jsonPath("$[0].codigoOperacion").value("OP-FLUJO"))
            .andExpect(jsonPath("$[0].carteraDigital").value("PLIN"))
    }

    @Test
    fun `GET loans current phone - SOLICITUD antes y PRESTAMO_ACTIVO despues del registro`() {
        mockMvc.perform(get("/loans/current/phone/987654321"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tipo").value("SOLICITUD"))
            .andExpect(jsonPath("$.idSolicitud").value(pending.id.toString()))
            .andExpect(jsonPath("$.estado").value("PENDIENTE"))
            .andExpect(jsonPath("$.montoSolicitado").value(200))

        val loan = register(body()).andExpect(status().isCreated).json()

        val current = mockMvc.perform(get("/loans/current/phone/987654321")).andExpect(status().isOk).json()
        assertEquals("PRESTAMO_ACTIVO", current["tipo"].asText())
        assertEquals(loan["idPrestamo"].asText(), current["idPrestamo"].asText())
        assertEquals(BigDecimal("59.00"), current["montoCuota"].decimalValue())
        assertEquals(1, current["numeroCuota"].asInt())
        assertEquals(4, current["totalCuotas"].asInt())
        assertEquals("2026-11-29", current["fechaVencimiento"].asText())
        assertEquals(BigDecimal("236.00"), current["saldoPendiente"].decimalValue())

        // Pagada la 1ra cuota, la próxima es la 2da
        val first = installmentRepository.findAllByLoanIdOrderByInstallmentNumberAsc(UUID.fromString(loan["idPrestamo"].asText()))[0]
        first.status = InstallmentStatus.PAGADA
        installmentRepository.save(first)
        mockMvc.perform(get("/loans/current/phone/987654321"))
            .andExpect(jsonPath("$.numeroCuota").value(2))
            .andExpect(jsonPath("$.fechaVencimiento").value("2026-12-14"))
    }

    @Test
    fun `GET loans current phone - 404 sin prestamo ni solicitud y 400 con celular invalido`() {
        mockMvc.perform(get("/loans/current/phone/911111111")).andExpect(status().isNotFound)
        mockMvc.perform(get("/loans/current/phone/12ab")).andExpect(status().isBadRequest)
        assertNotNull(pending.id)
    }
}
