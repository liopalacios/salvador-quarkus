package com.join8.identifyr.service

import com.join8.identifyr.exception.ConflictException
import com.join8.identifyr.model.dto.request.LoanRegisterRequest
import com.join8.identifyr.model.dto.response.LoanRegisterResponse
import com.join8.identifyr.model.entity.*
import com.join8.identifyr.repository.LoanRepository
import com.join8.identifyr.repository.LoanRequestRepository
import com.join8.identifyr.repository.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/** Dos supervisores guardan a la vez: uno obtiene el préstamo y el otro un 409 (ConflictException). */
@SpringBootTest
@ActiveProfiles("test")
class LoanServiceConcurrencyTest {

    @Autowired lateinit var service: LoanService
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var loanRequestRepository: LoanRequestRepository
    @Autowired lateinit var loanRepository: LoanRepository
    @MockitoBean lateinit var clock: Clock

    @BeforeEach
    fun fixClock() {
        `when`(clock.instant()).thenReturn(Instant.parse("2026-11-01T19:32:10Z"))
    }

    @AfterEach
    fun cleanUp() {
        loanRepository.deleteAll()
        loanRequestRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun pendingRequest(): LoanRequest {
        val userId = userRepository.save(
            User(phone = "9${(10_000_000..99_999_999).random()}", email = "", photoFront = UUID.randomUUID().toString(),
                photoBack = UUID.randomUUID().toString(), passwordHash = "")
        ).id!!
        return loanRequestRepository.save(
            LoanRequest(userId = userId, phone = "987654321", depositType = DepositType.YAPE,
                category = LoanCategory.ORO, score = 72, requestedAmount = 200)
        )
    }

    private fun body(requestId: UUID, operationCode: String) = LoanRegisterRequest(
        idSolicitud = requestId.toString(), monto = 200, codigoOperacion = operationCode, puntaje = 72,
        categoria = "ORO", interes = 36, fechaPago = LocalDate.parse("2026-11-08"),
        fechaPagoApp = LocalDate.parse("2027-01-01")
    )

    /** Lanza todas las llamadas a la vez y devuelve el resultado de cada una (respuesta o excepción). */
    private fun runConcurrently(vararg calls: () -> LoanRegisterResponse): List<Result<LoanRegisterResponse>> {
        val pool = Executors.newFixedThreadPool(calls.size)
        val start = CountDownLatch(1)
        try {
            val futures = calls.map { call -> pool.submit<Result<LoanRegisterResponse>> { start.await(); runCatching(call) } }
            start.countDown()
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `misma solicitud a la vez - uno registra y el otro recibe 409 con el id existente`() {
        repeat(5) {   // varias rondas para cubrir distintos entrelazados
            val request = pendingRequest()
            val results = runConcurrently(
                { service.register(body(request.id!!, "OP-A-$it"), "supervisor-1") },
                { service.register(body(request.id!!, "OP-B-$it"), "supervisor-2") }
            )

            val ok = results.mapNotNull { it.getOrNull() }
            val conflicts = results.mapNotNull { it.exceptionOrNull() }
            assertEquals(1, ok.size, "ronda $it: resultados $results")
            assertEquals(1, conflicts.size, "ronda $it: resultados $results")
            val conflict = conflicts.single() as ConflictException
            assertEquals(ok.single().id, conflict.idPrestamo)

            assertEquals(1, loanRepository.findAll().count { l -> l.loanRequestId == request.id })
            val reloaded = loanRequestRepository.findById(request.id!!).get()
            assertEquals(LoanRequestStatus.PROCESADA, reloaded.status)
            assertEquals(ok.single().codigoOperacion, reloaded.operationCode)
        }
    }

    @Test
    fun `mismo codigoOperacion en dos solicitudes a la vez - uno registra y el otro recibe 409`() {
        repeat(5) {
            val first = pendingRequest()
            val second = pendingRequest()
            val code = "OP-DUP-$it"
            val results = runConcurrently(
                { service.register(body(first.id!!, code), "supervisor-1") },
                { service.register(body(second.id!!, code), "supervisor-2") }
            )

            assertEquals(1, results.count { r -> r.isSuccess }, "ronda $it: resultados $results")
            val failure = results.single { r -> r.isFailure }.exceptionOrNull()!!
            assertEquals(ConflictException::class, failure::class, "ronda $it: $failure")

            // La solicitud perdedora sigue PENDIENTE y sin código: no quedó nada a medias
            val loserId = if (results[0].isSuccess) second.id!! else first.id!!
            val loser = loanRequestRepository.findById(loserId).get()
            assertEquals(LoanRequestStatus.PENDIENTE, loser.status)
            assertEquals(null, loser.operationCode)
        }
    }
}
