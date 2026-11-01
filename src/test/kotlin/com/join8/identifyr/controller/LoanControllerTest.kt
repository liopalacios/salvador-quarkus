package com.join8.identifyr.controller

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.join8.identifyr.model.entity.*
import com.join8.identifyr.repository.LoanRepository
import com.join8.identifyr.repository.LoanRequestRepository
import com.join8.identifyr.repository.UserRepository
import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
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
import org.hamcrest.Matchers.hasSize
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
import kotlin.test.assertNull

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoanControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var loanRequestRepository: LoanRequestRepository
    @Autowired lateinit var loanRepository: LoanRepository
    @MockitoBean lateinit var clock: Clock
    @Value("\${jwt.secret}") lateinit var jwtSecret: String

    // 2026-11-01 14:32:10 en Lima (UTC-5)
    private val defaultNow = Instant.parse("2026-11-01T19:32:10Z")
    private lateinit var supervisorToken: String
    private lateinit var pending: LoanRequest

    @BeforeEach
    fun setUp() {
        `when`(clock.instant()).thenReturn(defaultNow)
        supervisorToken = token(listOf("SUPERVISOR"))
        pending = loanRequest()
    }

    @AfterEach
    fun cleanUp() {
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
        category: LoanCategory = LoanCategory.ORO,
        score: Int = 72
    ): LoanRequest {
        val userId = userRepository.save(
            User(phone = "9${(10_000_000..99_999_999).random()}", email = "", photoFront = UUID.randomUUID().toString(),
                photoBack = UUID.randomUUID().toString(), passwordHash = "")
        ).id!!
        return loanRequestRepository.save(
            LoanRequest(userId = userId, phone = "987654321", depositType = DepositType.PLIN, category = category,
                score = score, requestedAmount = amount, status = status)
        )
    }

    private fun body(
        idSolicitud: Any? = pending.id,
        monto: Any? = 200,
        codigoOperacion: Any? = "OP-458712",
        puntaje: Any? = 72,
        categoria: Any? = "ORO",
        interes: Any? = 36,
        fechaPago: Any? = "2026-11-08",
        fechaPagoApp: Any? = "2027-01-01"
    ): String {
        fun json(v: Any?) = when (v) {
            null -> "null"
            is Number -> v.toString()
            else -> "\"$v\""
        }
        return """
            {"idSolicitud":${json(idSolicitud)},"monto":${json(monto)},"codigoOperacion":${json(codigoOperacion)},
             "puntaje":${json(puntaje)},"categoria":${json(categoria)},"interes":${json(interes)},
             "fechaPago":${json(fechaPago)},"fechaPagoApp":${json(fechaPagoApp)}}
        """
    }

    private fun register(json: String, authorization: String? = "Bearer $supervisorToken"): ResultActions {
        val request = post("/loans/register").contentType(MediaType.APPLICATION_JSON).content(json)
        authorization?.let { request.header(HttpHeaders.AUTHORIZATION, it) }
        return mockMvc.perform(request)
    }

    private fun JsonNode.keys() = fieldNames().asSequence().toSet()

    private fun assertRequestUntouched(request: LoanRequest = pending) {
        val reloaded = loanRequestRepository.findById(request.id!!).get()
        assertEquals(request.status, reloaded.status)
        assertNull(reloaded.operationCode)
    }

    // --- tests ---

    @Test
    fun `registro valido devuelve 201, guarda el prestamo VIGENTE y pasa la solicitud a PROCESADA`() {
        register(body())
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.idSolicitud").value(pending.id.toString()))
            .andExpect(jsonPath("$.monto").value(200))
            .andExpect(jsonPath("$.interes").value(36))
            .andExpect(jsonPath("$.totalAPagar").value(236))
            .andExpect(jsonPath("$.codigoOperacion").value("OP-458712"))
            .andExpect(jsonPath("$.fechaPago").value("2026-11-08"))
            .andExpect(jsonPath("$.fechaPagoApp").value("2027-01-01"))
            .andExpect(jsonPath("$.estado").value("VIGENTE"))
            .andExpect(jsonPath("$.fechaRegistro").value("2026-11-01T19:32:10Z"))

        val loan = loanRepository.findByLoanRequestId(pending.id!!)!!
        assertEquals(LoanStatus.VIGENTE, loan.status)
        assertEquals(200, loan.amount)
        assertEquals(36, loan.interest)
        assertEquals(0, BigDecimal("0.18").compareTo(loan.interestRate))
        assertEquals(236, loan.totalAmount)
        assertEquals(LocalDate.parse("2026-11-08"), loan.paymentDate)
        assertEquals(LocalDate.parse("2027-01-01"), loan.appPaymentDate)
        assertEquals(pending.userId, loan.userId)
        assertEquals(72, loan.score)
        assertEquals(LoanCategory.ORO, loan.category)
        assertEquals(DepositType.PLIN, loan.depositType)   // tomado de la solicitud
        assertEquals("supervisor-1", loan.createdBy)

        val request = loanRequestRepository.findById(pending.id!!).get()
        assertEquals(LoanRequestStatus.PROCESADA, request.status)
        assertEquals("OP-458712", request.operationCode)
    }

    @Test
    fun `interes es floor de monto por 0_18`() {
        val req = loanRequest(amount = 333)            // 333 * 0.18 = 59.94 -> 59
        register(body(idSolicitud = req.id, monto = 333, interes = 59))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.interes").value(59))
            .andExpect(jsonPath("$.totalAPagar").value(392))

        val bronce = loanRequest(amount = 50, category = LoanCategory.BRONCE, score = 10)   // 9.0 -> 9
        register(body(idSolicitud = bronce.id, monto = 50, interes = 9, categoria = "BRONCE", puntaje = 10,
            codigoOperacion = "OP-2"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.totalAPagar").value(59))
    }

    @Test
    fun `las fechas usan el dia en America Lima y no en UTC`() {
        // 2026-11-01 23:30 en Lima = 2026-11-02 04:30 UTC
        `when`(clock.instant()).thenReturn(Instant.parse("2026-11-02T04:30:00Z"))

        // Con el día UTC (02) la app mandaría 09/11 y 02/01: se rechaza indicando lo esperado
        register(body(fechaPago = "2026-11-09", fechaPagoApp = "2027-01-02"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message", allOf(
                containsString("fechaPago: valor esperado 2026-11-08"),
                containsString("fechaPagoApp: valor esperado 2027-01-01")
            )))

        register(body())
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.fechaPago").value("2026-11-08"))
            .andExpect(jsonPath("$.fechaPagoApp").value("2027-01-01"))
    }

    @Test
    fun `pasada la medianoche en Lima las fechas avanzan un dia`() {
        // 2026-11-02 00:00 en Lima
        `when`(clock.instant()).thenReturn(Instant.parse("2026-11-02T05:00:00Z"))
        register(body(fechaPago = "2026-11-09", fechaPagoApp = "2027-01-02"))
            .andExpect(status().isCreated)
    }

    @Test
    fun `interes o fechas distintas del calculo devuelven 400 con el valor esperado`() {
        register(body(interes = 37)).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message", containsString("interes: valor esperado 36")))
        register(body(fechaPago = "2026-11-07")).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message", containsString("fechaPago: valor esperado 2026-11-08")))
        register(body(fechaPagoApp = "2026-12-31")).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message", containsString("fechaPagoApp: valor esperado 2027-01-01")))

        assertEquals(0, loanRepository.count())
        assertRequestUntouched()
    }

    @Test
    fun `monto, puntaje o categoria distintos de la solicitud devuelven 400`() {
        register(body(monto = 150, interes = 27)).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message", containsString("monto: debe ser igual al monto de la solicitud (200)")))
        register(body(puntaje = 73)).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message", containsString("puntaje: debe coincidir con el de la solicitud (72)")))
        register(body(categoria = "PLATA")).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message", containsString("categoria: debe coincidir con la de la solicitud (ORO)")))

        assertEquals(0, loanRepository.count())
        assertRequestUntouched()
    }

    @Test
    fun `categoria invalida, codigoOperacion vacio y formatos invalidos devuelven 400`() {
        listOf(
            body(categoria = "DIAMANTE"),
            body(categoria = "oro"),
            body(codigoOperacion = ""),
            body(codigoOperacion = "    "),
            body(codigoOperacion = null),
            body(codigoOperacion = "X".repeat(51)),
            body(idSolicitud = "no-es-uuid"),
            body(monto = 0),
            body(puntaje = -1),
            body(interes = -1),
            body(fechaPago = "08/11/2026"),
            body(fechaPagoApp = "2027-13-01"),
            body(fechaPago = null),
            "{}",
            "{no es json"
        ).forEach { json -> register(json).andExpect(status().isBadRequest) }

        register(body(codigoOperacion = "  ")).andExpect(jsonPath("$.message", containsString("codigoOperacion: es obligatorio")))
        register(body(categoria = "DIAMANTE"))
            .andExpect(jsonPath("$.message", containsString("categoria: solo se permite ORO, PLATA o BRONCE")))
        register(body(fechaPago = "08/11/2026")).andExpect(jsonPath("$.message", containsString("fechaPago")))

        assertEquals(0, loanRepository.count())
        assertRequestUntouched()
    }

    @Test
    fun `codigoOperacion se guarda sin espacios alrededor`() {
        register(body(codigoOperacion = "  OP-458712  "))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.codigoOperacion").value("OP-458712"))
        assertEquals("OP-458712", loanRepository.findByLoanRequestId(pending.id!!)!!.operationCode)
    }

    @Test
    fun `solicitud inexistente o que no esta PENDIENTE devuelve 422 y nunca 404`() {
        register(body(idSolicitud = UUID.randomUUID()))
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.message", containsString("no existe")))

        listOf(LoanRequestStatus.RECHAZADA, LoanRequestStatus.PROCESADA).forEach { st ->
            val req = loanRequest(status = st)
            register(body(idSolicitud = req.id))
                .andExpect(status().isUnprocessableEntity)
                .andExpect(jsonPath("$.message", containsString("no está PENDIENTE")))
            assertRequestUntouched(req)
        }
        assertEquals(0, loanRepository.count())
    }

    @Test
    fun `segundo prestamo para la misma solicitud devuelve 409 con el id existente`() {
        val first = objectMapper.readTree(
            register(body()).andExpect(status().isCreated).andReturn().response.contentAsString
        )["id"].asText()

        // Reintento idéntico (corte de red) y reintento al día siguiente: 409 con el id, no 400/422
        register(body())
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.idPrestamo").value(first))
        `when`(clock.instant()).thenReturn(defaultNow.plus(1, ChronoUnit.DAYS))
        register(body(codigoOperacion = "OP-OTRO"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.idPrestamo").value(first))

        assertEquals(1, loanRepository.count())
    }

    @Test
    fun `codigoOperacion repetido devuelve 409`() {
        register(body()).andExpect(status().isCreated)

        val other = loanRequest()
        register(body(idSolicitud = other.id, codigoOperacion = " OP-458712 "))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message", containsString("ya se usó en otro préstamo")))
            .andExpect(jsonPath("$.idPrestamo").doesNotExist())

        assertEquals(1, loanRepository.count())
        assertRequestUntouched(other)
    }

    @Test
    fun `con token invalido 401 y sin rol supervisor 403`() {
        register(body(), authorization = "Bearer no-es-un-jwt").andExpect(status().isUnauthorized)
        register(body(), authorization = "Bearer ${token(listOf("USER"))}").andExpect(status().isForbidden)
        assertEquals(0, loanRepository.count())
        assertRequestUntouched()
    }

    @Test
    fun `sin token o con token vacio o null se permite mientras allow-anonymous=true y created_by queda null`() {
        listOf(null, "", "Bearer", "Bearer ", "Bearer null").forEachIndexed { i, header ->
            val req = loanRequest()
            register(body(idSolicitud = req.id, codigoOperacion = "OP-ANON-$i"), authorization = header)
                .andExpect(status().isCreated)
            assertNull(loanRepository.findByLoanRequestId(req.id!!)!!.createdBy)
            assertEquals(LoanRequestStatus.PROCESADA, loanRequestRepository.findById(req.id!!).get().status)
        }
    }

    @Test
    fun `tras registrar el prestamo la solicitud sale de Pendientes y aparece en Procesadas con su codigo`() {
        register(body(codigoOperacion = "OP-FLUJO"), authorization = "Bearer null").andExpect(status().isCreated)

        // Mismo flujo que la pantalla del supervisor, también sin token
        mockMvc.perform(get("/loan-requests?estado=PENDIENTE"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items", hasSize<Any>(0)))
            .andExpect(jsonPath("$.conteos.PENDIENTE").value(0))
            .andExpect(jsonPath("$.conteos.PROCESADA").value(1))
        mockMvc.perform(get("/loan-requests?estado=PROCESADA"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].id").value(pending.id.toString()))
            .andExpect(jsonPath("$.items[0].estado").value("PROCESADA"))
            .andExpect(jsonPath("$.items[0].codigoOperacion").value("OP-FLUJO"))
    }

    @Test
    fun `si el registro del prestamo falla la solicitud no cambia de estado`() {
        register(body(interes = 99)).andExpect(status().isBadRequest)          // validación
        register(body(), authorization = "Bearer ${token(listOf("USER"))}")   // seguridad
            .andExpect(status().isForbidden)
        assertEquals(0, loanRepository.count())
        assertRequestUntouched()
    }

    @Test
    fun `las claves JSON del request y la response son exactamente las del ejemplo`() {
        // Mismos datos con otros nombres -> no se reconocen -> 400
        register(
            """{"requestId":"${pending.id}","amount":200,"operationCode":"OP-1","score":72,
                "category":"ORO","interest":36,"paymentDate":"2026-11-08","appPaymentDate":"2027-01-01"}"""
        ).andExpect(status().isBadRequest)
        register(
            """{"id_solicitud":"${pending.id}","monto":200,"codigo_operacion":"OP-1","puntaje":72,
                "categoria":"ORO","interes":36,"fecha_pago":"2026-11-08","fecha_pago_app":"2027-01-01"}"""
        ).andExpect(status().isBadRequest)
        assertEquals(0, loanRepository.count())

        // Claves exactas del ejemplo -> 201 con las claves exactas de la respuesta
        val response = objectMapper.readTree(
            register(
                """{"idSolicitud":"${pending.id}","monto":200,"codigoOperacion":"OP-458712","puntaje":72,
                    "categoria":"ORO","interes":36,"fechaPago":"2026-11-08","fechaPagoApp":"2027-01-01"}"""
            ).andExpect(status().isCreated).andReturn().response.contentAsString
        )
        assertEquals(
            setOf("id", "idSolicitud", "monto", "interes", "totalAPagar", "codigoOperacion",
                "fechaPago", "fechaPagoApp", "estado", "fechaRegistro"),
            response.keys()
        )

        val conflict = objectMapper.readTree(register(body()).andReturn().response.contentAsString)
        assertEquals(response["id"].asText(), conflict["idPrestamo"].asText())
    }
}
