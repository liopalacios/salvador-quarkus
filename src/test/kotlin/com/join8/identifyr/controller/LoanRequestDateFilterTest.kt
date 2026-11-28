package com.join8.identifyr.controller

import com.join8.identifyr.model.entity.DepositType
import com.join8.identifyr.model.entity.LoanCategory
import com.join8.identifyr.model.entity.LoanRequest
import com.join8.identifyr.model.entity.LoanRequestStatus
import com.join8.identifyr.model.entity.User
import com.join8.identifyr.repository.LoanRequestRepository
import com.join8.identifyr.repository.UserRepository
import com.join8.identifyr.support.TestTokens
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.test.Test

/** GET /loan-requests: sin fecha, los últimos 30 días (hoy incluido, hora de Lima); con fecha, solo ese día. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoanRequestDateFilterTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var loanRequestRepository: LoanRequestRepository
    @Value("\${jwt.secret}") lateinit var jwtSecret: String

    private val lima = ZoneId.of("America/Lima")
    private val today get() = LocalDate.now(lima)

    @AfterEach
    fun cleanUp() {
        loanRequestRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun at(day: LocalDate, time: LocalTime = LocalTime.NOON): Instant =
        day.atTime(time).atZone(lima).toInstant().truncatedTo(ChronoUnit.MICROS)

    private fun request(createdAt: Instant, status: LoanRequestStatus = LoanRequestStatus.PENDIENTE): LoanRequest {
        val user = userRepository.save(User(phone = "9${(10_000_000..99_999_999).random()}"))
        return loanRequestRepository.save(
            LoanRequest(
                userId = user.id!!, phone = user.phone, depositType = DepositType.YAPE, category = LoanCategory.ORO,
                score = 720, requestedAmount = 200, status = status,
                processedAt = if (status == LoanRequestStatus.PROCESADA) createdAt else null,
                operationCode = if (status == LoanRequestStatus.PROCESADA) "OP-${createdAt.toEpochMilli()}" else null,
                createdAt = createdAt, updatedAt = createdAt
            )
        )
    }

    private fun list(query: String) = mockMvc.perform(
        get("/loan-requests?$query").header(HttpHeaders.AUTHORIZATION, "Bearer ${TestTokens.supervisor(jwtSecret)}")
    )

    @Test
    fun `sin fecha devuelve solo los ultimos 30 dias, hoy incluido`() {
        val todayReq = request(at(today))
        val tenDays = request(at(today.minusDays(10)))
        val firstDay = request(at(today.minusDays(29), LocalTime.MIDNIGHT))          // primer día del rango
        request(at(today.minusDays(30), LocalTime.of(23, 59, 59)))                  // un segundo antes del rango
        request(at(today.minusDays(40)))

        list("estado=PENDIENTE")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
            // PENDIENTE: más antigua primero
            .andExpect(jsonPath("$[0].idSolicitud").value(firstDay.id.toString()))
            .andExpect(jsonPath("$[1].idSolicitud").value(tenDays.id.toString()))
            .andExpect(jsonPath("$[2].idSolicitud").value(todayReq.id.toString()))
    }

    @Test
    fun `con fecha devuelve solo las solicitudes de ese dia, aunque sea de hace mas de 30 dias`() {
        val day = today.minusDays(45)
        val morning = request(at(day, LocalTime.of(0, 0)))
        val night = request(at(day, LocalTime.of(23, 59, 59)))
        request(at(day.minusDays(1), LocalTime.of(23, 59, 59)))
        request(at(day.plusDays(1), LocalTime.MIDNIGHT))
        request(at(today))

        list("estado=PENDIENTE&fecha=$day")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].idSolicitud").value(morning.id.toString()))
            .andExpect(jsonPath("$[1].idSolicitud").value(night.id.toString()))
    }

    @Test
    fun `la fecha aplica a las tres pestanas`() {
        val day = today.minusDays(5)
        request(at(day), LoanRequestStatus.PROCESADA)
        request(at(today), LoanRequestStatus.PROCESADA)
        request(at(day), LoanRequestStatus.RECHAZADA)
        request(at(today.minusDays(60)), LoanRequestStatus.RECHAZADA)

        list("estado=PROCESADA&fecha=$day").andExpect(jsonPath("$.length()").value(1))
        list("estado=PROCESADA").andExpect(jsonPath("$.length()").value(2))
        list("estado=RECHAZADA&fecha=$day").andExpect(jsonPath("$.length()").value(1))
        list("estado=RECHAZADA").andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `fecha vacia equivale a sin fecha y una fecha sin solicitudes devuelve lista vacia`() {
        request(at(today))
        list("estado=PENDIENTE&fecha=").andExpect(jsonPath("$.length()").value(1))
        list("estado=PENDIENTE&fecha=${today.plusDays(3)}").andExpect(status().isOk).andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `fecha con formato invalido 400`() {
        list("estado=PENDIENTE&fecha=28-11-2026")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("fecha debe tener el formato yyyy-MM-dd"))
        list("estado=PENDIENTE&fecha=2026-02-30").andExpect(status().isBadRequest)
    }
}
