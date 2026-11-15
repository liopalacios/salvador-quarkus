package com.join8.identifyr.service

import com.join8.identifyr.exception.ConflictException
import com.join8.identifyr.model.dto.request.LoanRegisterRequest
import com.join8.identifyr.model.dto.response.LoanRegisterResponse
import com.join8.identifyr.model.entity.*
import com.join8.identifyr.repository.LoanInstallmentRepository
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
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Dos supervisores guardan a la vez: uno crea el préstamo y el otro recibe 409 (ConflictException). */
@SpringBootTest
@ActiveProfiles("test")
class LoanServiceConcurrencyTest {

    @Autowired lateinit var service: LoanService
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var loanRequestRepository: LoanRequestRepository
    @Autowired lateinit var loanRepository: LoanRepository
    @Autowired lateinit var installmentRepository: LoanInstallmentRepository
    @MockitoBean lateinit var clock: Clock

    @BeforeEach
    fun fixClock() {
        `when`(clock.instant()).thenReturn(Instant.parse("2026-11-14T15:32:05Z"))
    }

    @AfterEach
    fun cleanUp() {
        installmentRepository.deleteAll()
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
                category = LoanCategory.ORO, score = 72, requestedAmount = 200, installmentFrequency = 15)
        )
    }

    private fun body(requestId: UUID, operationCode: String) =
        LoanRegisterRequest(idSolicitud = requestId.toString(), codigoOperacion = operationCode)

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
    fun `misma solicitud a la vez - uno crea el prestamo y el otro recibe 409`() {
        repeat(5) {   // varias rondas para cubrir distintos entrelazados
            val request = pendingRequest()
            val results = runConcurrently(
                { service.register(body(request.id!!, "OP-A-$it"), "supervisor-1") },
                { service.register(body(request.id!!, "OP-B-$it"), "supervisor-2") }
            )

            val ok = results.mapNotNull { r -> r.getOrNull() }
            val failures = results.mapNotNull { r -> r.exceptionOrNull() }
            assertEquals(1, ok.size, "ronda $it: resultados $results")
            assertEquals(1, failures.size, "ronda $it: resultados $results")
            val conflict = failures.single() as ConflictException
            assertEquals(ok.single().idPrestamo, conflict.idPrestamo)

            val loans = loanRepository.findAll().filter { l -> l.loanRequestId == request.id }
            assertEquals(1, loans.size)
            assertEquals(4, installmentRepository.findAllByLoanIdOrderByInstallmentNumberAsc(loans.single().id!!).size)
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

            // La solicitud perdedora sigue PENDIENTE, sin código y sin préstamo ni cuotas: nada a medias
            val loserId = if (results[0].isSuccess) second.id!! else first.id!!
            val loser = loanRequestRepository.findById(loserId).get()
            assertEquals(LoanRequestStatus.PENDIENTE, loser.status)
            assertNull(loser.operationCode)
            assertNull(loanRepository.findByLoanRequestId(loserId))
        }
        assertEquals(loanRepository.count() * 4, installmentRepository.count())
    }
}
