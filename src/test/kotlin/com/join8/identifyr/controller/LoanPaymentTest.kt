package com.join8.identifyr.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.join8.identifyr.model.entity.*
import com.join8.identifyr.repository.*
import com.join8.identifyr.service.credit.CreditEngine
import com.join8.identifyr.service.credit.CreditParameterService
import com.join8.identifyr.service.credit.CreditSimulation
import com.join8.identifyr.service.credit.LegacyLoanRecalculator
import com.join8.identifyr.service.credit.Modality
import com.join8.identifyr.support.TestTokens
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Pagos con el motor de crédito: el cliente informa CUOTA (la ACTIVA) o TOTAL; el monto se liquida con la fecha
 * de hoy y queda PENDIENTE. El supervisor confirma (cuotas PAGADAS, la siguiente ACTIVA, saldo; préstamo PAGADO
 * al terminar) o rechaza (notificación al cliente). Tras cancelar, 48 h para solicitar otro préstamo.
 * Reloj simulado: hoy = desembolso (14/11/2026 10:00 en Lima), salvo donde se mueve.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoanPaymentTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var loanRequestRepository: LoanRequestRepository
    @Autowired lateinit var loanRepository: LoanRepository
    @Autowired lateinit var installmentRepository: LoanInstallmentRepository
    @Autowired lateinit var paymentRepository: LoanPaymentRepository
    @Autowired lateinit var paymentItemRepository: LoanPaymentItemRepository
    @Autowired lateinit var creditParameterService: CreditParameterService
    @Autowired lateinit var legacyLoanRecalculator: LegacyLoanRecalculator
    @MockitoBean lateinit var clock: Clock
    @Value("\${jwt.secret}") lateinit var jwtSecret: String

    private val lima = ZoneId.of("America/Lima")
    private val disbursedAt = Instant.parse("2026-11-14T15:00:00Z")
    private val disbursementDate = LocalDate.parse("2026-11-14")

    private lateinit var user: User
    private lateinit var loanId: UUID
    private lateinit var sim: CreditSimulation

    private val supervisor get() = "Bearer ${TestTokens.supervisor(jwtSecret, "supervisor-1")}"
    private val client get() = "Bearer ${TestTokens.user(jwtSecret, user.id!!, user.phone)}"

    private fun setNow(instant: Instant) {
        `when`(clock.instant()).thenReturn(instant)
        `when`(clock.millis()).thenReturn(instant.toEpochMilli())
    }

    @BeforeEach
    fun createLoan() {
        setNow(disbursedAt)
        // Con fotos del DNI: así la única notificación posible es la del pago rechazado
        user = userRepository.save(User(phone = "987654321", photoFront = "f", photoBack = "b"))
        loanId = disburse(200, 15)
        sim = CreditEngine.simulate(
            BigDecimal(200), Modality.QUINCENAL, disbursementDate,
            creditParameterService.parametersAt(disbursementDate), creditParameterService.holidays()
        )
    }

    @AfterEach
    fun cleanUp() {
        paymentItemRepository.deleteAll()
        paymentRepository.deleteAll()
        installmentRepository.deleteAll()
        loanRepository.deleteAll()
        loanRequestRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun disburse(amount: Int, frequency: Int): UUID {
        val request = loanRequestRepository.save(
            LoanRequest(
                userId = user.id!!, phone = user.phone, depositType = DepositType.YAPE, category = LoanCategory.ORO,
                score = 720, requestedAmount = amount, installmentFrequency = frequency
            )
        )
        val body = perform(
            post("/loans/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"idSolicitud":"${request.id}","codigoOperacion":"DESEMBOLSO-${request.id}"}"""),
            supervisor
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return UUID.fromString(objectMapper.readTree(body)["idPrestamo"].asText())
    }

    private fun perform(builder: MockHttpServletRequestBuilder, authorization: String?): ResultActions =
        mockMvc.perform(builder.apply { authorization?.let { header(HttpHeaders.AUTHORIZATION, it) } })

    private fun pay(type: String, code: String, authorization: String? = client, loan: UUID = loanId) = perform(
        post("/loans/$loan/payments").contentType(MediaType.APPLICATION_JSON)
            .content("""{"tipo":"$type","codigoOperacion":"$code"}"""),
        authorization
    )

    private fun paymentId(result: ResultActions): String =
        objectMapper.readTree(result.andReturn().response.contentAsString)["idPago"].asText()

    private fun confirm(id: String, authorization: String? = supervisor) = perform(post("/loan-payments/$id/confirm"), authorization)
    private fun reject(id: String, reason: String? = null) = perform(
        post("/loan-payments/$id/reject").apply { reason?.let { contentType(MediaType.APPLICATION_JSON).content("""{"motivo":"$it"}""") } },
        supervisor
    )

    private fun installments() = installmentRepository.findAllByLoanIdOrderByInstallmentNumberAsc(loanId)
    private fun loan() = loanRepository.findById(loanId).get()
    private fun current() = perform(get("/loans/current/phone/${user.phone}"), client)
    private fun notifications() = perform(get("/users/${user.id}/notifications"), client)

    // Día de Lima a las 10:00
    private fun on(date: String): Instant = LocalDate.parse(date).atTime(10, 0).atZone(lima).toInstant()

    // ---------- Registro del cliente: liquidación con la fecha de hoy ----------

    @Test
    fun `pagar la cuota el dia del desembolso no paga interes y el prestamo no cambia`() {
        val row = sim.schedule[0]
        pay("CUOTA", " OP-1 ")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.tipo").value("CUOTA"))
            .andExpect(jsonPath("$.numeroCuota").value(1))
            .andExpect(jsonPath("$.monto").value(row.principal.add(row.commission).add(row.apis).toDouble()))
            .andExpect(jsonPath("$.interes").value(0.0))
            .andExpect(jsonPath("$.fechaPago").value("2026-11-14"))
            .andExpect(jsonPath("$.codigoOperacion").value("OP-1"))
            .andExpect(jsonPath("$.estado").value("PENDIENTE"))

        assertEquals(sim.totalToPay, loan().outstandingBalance)
        assertEquals(listOf(InstallmentStatus.ACTIVA, InstallmentStatus.PENDIENTE, InstallmentStatus.PENDIENTE, InstallmentStatus.PENDIENTE),
            installments().map { it.status })
    }

    @Test
    fun `pagar la cuota en su vencimiento cobra la cuota del contrato y con atraso agrega interes y mora`() {
        setNow(on("2026-11-30"))  // vencimiento de la cuota 1
        pay("CUOTA", "OP-A").andExpect(jsonPath("$.monto").value(sim.installmentAmount.toDouble()))
        // Se rechaza para poder informar otro pago
        reject(paymentRepository.findAll().single().id.toString()).andExpect(status().isOk)

        setNow(on("2026-12-10"))  // 10 días tarde
        val late = CreditEngine.quoteInstallment(sim.schedule[0], disbursementDate, LocalDate.parse("2026-12-10"), sim.ted, sim.tedMora)
        pay("CUOTA", "OP-B")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.monto").value(late.amountToPay.toDouble()))
            .andExpect(jsonPath("$.interesMoratorio").value(late.moratorium.toDouble()))
        assert(late.amountToPay > sim.installmentAmount)
    }

    @Test
    fun `pago total cancela todas las cuotas con el descuento del dia (F28)`() {
        setNow(on("2026-11-22"))
        val payoff = CreditEngine.quotePayoff(sim.schedule, disbursementDate, LocalDate.parse("2026-11-22"), sim.ted, sim.tedMora)
        pay("TOTAL", "OP-T")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.monto").value(payoff.total.toDouble()))
            .andExpect(jsonPath("$.cuotas.length()").value(4))
            .andExpect(jsonPath("$.numeroCuota").doesNotExist())
        assert(payoff.total < sim.totalToPay)
    }

    @Test
    fun `el inicio muestra el pago pendiente y la cotizacion de hoy`() {
        pay("CUOTA", "OP-1")
        current()
            .andExpect(jsonPath("$.pagoPendiente.tipo").value("CUOTA"))
            .andExpect(jsonPath("$.pagoPendiente.codigoOperacion").value("OP-1"))
            .andExpect(jsonPath("$.cotizacionHoy.cuota.numeroCuota").value(1))
            .andExpect(jsonPath("$.cotizacionHoy.total.monto").value(265.0))
    }

    @Test
    fun `solo un pago pendiente a la vez y sin repetir codigo de operacion (409)`() {
        pay("CUOTA", "OP-1").andExpect(status().isCreated)
        pay("TOTAL", "OP-2").andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Ya tienes un pago pendiente de confirmación"))

        confirm(paymentRepository.findAll().single().id.toString()).andExpect(status().isOk)
        pay("CUOTA", "OP-1").andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("El código de operación ya fue registrado"))
    }

    @Test
    fun `validaciones del registro`() {
        pay("PARCIAL", "OP-1").andExpect(status().isBadRequest)
        pay("CUOTA", "   ").andExpect(status().isBadRequest)
        pay("CUOTA", "x".repeat(51)).andExpect(status().isBadRequest)
        pay("CUOTA", "OP-1", authorization = null).andExpect(status().isUnauthorized)
        pay("CUOTA", "OP-1", loan = UUID.randomUUID()).andExpect(status().isNotFound)

        val other = userRepository.save(User(phone = "912345678"))
        pay("CUOTA", "OP-1", authorization = "Bearer ${TestTokens.user(jwtSecret, other.id!!, other.phone)}")
            .andExpect(status().isNotFound)
        assertEquals(0, paymentRepository.count())
    }

    // ---------- Confirmación del supervisor ----------

    @Test
    fun `confirmar la cuota la marca PAGADA con lo pagado, la siguiente queda ACTIVA y baja el saldo`() {
        val id = paymentId(pay("CUOTA", "OP-1"))
        val paid = paymentRepository.findById(UUID.fromString(id)).get().amount

        confirm(id).andExpect(status().isOk).andExpect(jsonPath("$.estado").value("CONFIRMADO"))

        assertEquals(listOf(InstallmentStatus.PAGADA, InstallmentStatus.ACTIVA, InstallmentStatus.PENDIENTE, InstallmentStatus.PENDIENTE),
            installments().map { it.status })
        assertEquals(paid, installments()[0].paidAmount)
        assertEquals(sim.installmentAmount.multiply(BigDecimal(3)), loan().outstandingBalance)
        assertEquals(LoanStatus.VIGENTE, loan().status)

        current()
            .andExpect(jsonPath("$.numeroCuota").value(2))
            .andExpect(jsonPath("$.estadoCuota").value("ACTIVA"))
            .andExpect(jsonPath("$.cuotas[0].montoPagado").value(paid.toDouble()))
        pay("CUOTA", "OP-2").andExpect(jsonPath("$.numeroCuota").value(2))
    }

    @Test
    fun `al confirmar la ultima cuota o el total el prestamo queda PAGADO y el inicio lo muestra`() {
        val id = paymentId(pay("TOTAL", "OP-T"))

        confirm(id).andExpect(status().isOk)

        assertEquals(List(4) { InstallmentStatus.PAGADA }, installments().map { it.status })
        assertEquals(BigDecimal("0.00"), loan().outstandingBalance)
        assertEquals(LoanStatus.PAGADO, loan().status)
        assertNotNull(loan().closedAt)
        pay("CUOTA", "OP-X").andExpect(status().isUnprocessableEntity)

        val closedAt = loan().closedAt!!
        current()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tipo").value("PRESTAMO_PAGADO"))
            .andExpect(jsonPath("$.saldoPendiente").value(0.0))
            .andExpect(jsonPath("$.nuevaSolicitudDesde").value(closedAt.plus(Duration.ofHours(48)).toString()))
    }

    @Test
    fun `tras cancelar hay que esperar 48 h para solicitar otro prestamo`() {
        confirm(paymentId(pay("TOTAL", "OP-T")))
        val closedAt = loan().closedAt!!
        fun request() = perform(
            post("/loan-requests/register").contentType(MediaType.APPLICATION_JSON).content(
                """{"idUsuario":"${user.id}","numCelular":"${user.phone}","tipoDeposito":"YAPE","categoria":"ORO",""" +
                    """"puntaje":720,"montoSolicitado":200,"frecuenciaCuota":15}"""
            ),
            client
        )

        setNow(closedAt.plus(Duration.ofHours(47)))
        request().andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Podrás solicitar un nuevo préstamo desde el ")))

        setNow(closedAt.plus(Duration.ofHours(48)).plusSeconds(1))
        request().andExpect(status().isCreated)
    }

    @Test
    fun `con un prestamo vigente no puede solicitar otro`() {
        perform(
            post("/loan-requests/register").contentType(MediaType.APPLICATION_JSON).content(
                """{"idUsuario":"${user.id}","numCelular":"${user.phone}","tipoDeposito":"YAPE","categoria":"ORO",""" +
                    """"puntaje":720,"montoSolicitado":200,"frecuenciaCuota":15}"""
            ),
            client
        ).andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.message").value("Ya tienes un préstamo vigente: cancélalo para solicitar otro"))
    }

    // ---------- Rechazo del pago ----------

    @Test
    fun `rechazar el pago no cambia el prestamo, avisa al cliente y permite informar otro`() {
        val id = paymentId(pay("CUOTA", "OP-1"))

        reject(id, "No figura el depósito")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.estado").value("RECHAZADO"))
            .andExpect(jsonPath("$.motivoRechazo").value("No figura el depósito"))

        assertEquals(sim.totalToPay, loan().outstandingBalance)
        assertEquals(InstallmentStatus.ACTIVA, installments()[0].status)

        notifications()
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].type").value("PAYMENT_REJECTED"))
            .andExpect(jsonPath("$[0].referenceId").value(id))
            .andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.containsString("Motivo: No figura el depósito")))

        perform(
            post("/users/${user.id}/notifications/read").contentType(MediaType.APPLICATION_JSON)
                .content("""{"type":"PAYMENT_REJECTED","referenceId":"$id"}"""),
            client
        ).andExpect(status().isNoContent)
        notifications().andExpect(jsonPath("$.length()").value(0))

        // El código rechazado se puede volver a usar
        pay("CUOTA", "OP-1").andExpect(status().isCreated)
    }

    @Test
    fun `listado del supervisor con desglose y solo se confirma o rechaza un pago pendiente`() {
        val id = paymentId(pay("CUOTA", "OP-1"))

        perform(get("/loan-payments"), supervisor)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].idPago").value(id))
            .andExpect(jsonPath("$[0].numCelular").value("987654321"))
            .andExpect(jsonPath("$[0].numeroCuota").value(1))
            .andExpect(jsonPath("$[0].capital").value(sim.schedule[0].principal.toDouble()))
            .andExpect(jsonPath("$[0].comision").value(15.0))

        confirm(id).andExpect(status().isOk)
        confirm(id).andExpect(status().isConflict)
        perform(post("/loan-payments/$id/reject"), supervisor).andExpect(status().isConflict)
        confirm(UUID.randomUUID().toString()).andExpect(status().isNotFound)

        perform(get("/loan-payments?estado=PENDIENTE"), supervisor).andExpect(jsonPath("$.length()").value(0))
        perform(get("/loan-payments?estado=CONFIRMADO"), supervisor).andExpect(jsonPath("$.length()").value(1))
        perform(get("/loan-payments?estado=OTRO"), supervisor).andExpect(status().isBadRequest)
    }

    @Test
    fun `solo el supervisor lista, confirma o rechaza`() {
        val id = paymentId(pay("CUOTA", "OP-1"))
        perform(get("/loan-payments"), client).andExpect(status().isForbidden)
        confirm(id, authorization = client).andExpect(status().isForbidden)
        perform(post("/loan-payments/$id/reject"), client).andExpect(status().isForbidden)
        assertEquals(PaymentStatus.PENDIENTE, paymentRepository.findAll().single().status)
    }

    // ---------- Recálculo de préstamos antiguos ----------

    @Test
    fun `un prestamo creado con la formula anterior se recalcula con el motor`() {
        val legacy = loan()
        // Simula un préstamo antiguo: sin snapshot ni desglose, 4 cuotas de 59.00
        legacy.engineVersion = null
        legacy.ted = null
        legacy.tedMora = null
        loanRepository.saveAndFlush(legacy)
        installments().forEach {
            it.amount = BigDecimal("59.00"); it.interest = null; it.principal = null; it.tDays = null
            it.status = InstallmentStatus.PENDIENTE
        }
        installmentRepository.saveAllAndFlush(installments())

        assertEquals(1, legacyLoanRecalculator.recalculateAll())

        val recalculated = loan()
        assertEquals(3, recalculated.engineVersion)
        assertEquals(sim.totalToPay, recalculated.outstandingBalance)
        assertEquals(List(4) { sim.installmentAmount }, installments().map { it.amount })
        assertEquals(InstallmentStatus.ACTIVA, installments()[0].status)
        assertNull(installments().firstOrNull { it.principal == null })
        // Idempotente
        assertEquals(0, legacyLoanRecalculator.recalculateAll())
    }
}
