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
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Rechazo de solicitudes: POST /loan-requests/{id}/reject (supervisor), cómo lo ve el cliente en
 * GET /loans/current/phone/{phone} y la notificación LOAN_REQUEST_REJECTED (y marcarla como leída).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoanRequestRejectTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var loanRequestRepository: LoanRequestRepository
    @Value("\${jwt.secret}") lateinit var jwtSecret: String

    private lateinit var user: User

    @BeforeEach
    fun createUser() {
        user = userRepository.save(User(phone = "987654321", photoFront = "f", photoBack = "b"))
    }

    @AfterEach
    fun cleanUp() {
        loanRequestRepository.deleteAll()
        userRepository.deleteAll()
    }

    private val supervisor get() = "Bearer ${TestTokens.supervisor(jwtSecret, "supervisor-1")}"
    private val client get() = "Bearer ${TestTokens.user(jwtSecret, user.id!!, user.phone)}"

    private fun request(status: LoanRequestStatus = LoanRequestStatus.PENDIENTE, createdAt: java.time.Instant = LoanRequest.now()) =
        loanRequestRepository.save(
            LoanRequest(
                userId = user.id!!, phone = user.phone, depositType = DepositType.YAPE, category = LoanCategory.ORO,
                score = 720, requestedAmount = 300, installmentFrequency = 30, status = status,
                createdAt = createdAt, updatedAt = createdAt
            )
        )

    private fun perform(builder: MockHttpServletRequestBuilder, authorization: String?): ResultActions =
        mockMvc.perform(builder.apply { authorization?.let { header(HttpHeaders.AUTHORIZATION, it) } })

    private fun reject(id: UUID, body: String?, authorization: String? = supervisor): ResultActions =
        perform(
            post("/loan-requests/$id/reject").apply { body?.let { contentType(MediaType.APPLICATION_JSON).content(it) } },
            authorization
        )

    private fun markRead(type: String, referenceId: String?) = perform(
        post("/users/${user.id}/notifications/read").contentType(MediaType.APPLICATION_JSON)
            .content("""{"type":"$type"${referenceId?.let { ""","referenceId":"$it"""" } ?: ""}}"""),
        client
    )

    // ---------- Rechazo por el supervisor ----------

    @Test
    fun `rechaza una solicitud pendiente con motivo y la lista en RECHAZADA`() {
        val pending = request()

        reject(pending.id!!, """{"motivo":"  Score insuficiente  "}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.estado").value("RECHAZADA"))
            .andExpect(jsonPath("$.motivo").value("Score insuficiente"))
            .andExpect(jsonPath("$.fechaRechazo").isNotEmpty)

        val saved = loanRequestRepository.findById(pending.id!!).get()
        assertEquals(LoanRequestStatus.RECHAZADA, saved.status)
        assertEquals("supervisor-1", saved.rejectedBy)
        assertNotNull(saved.rejectedAt)

        perform(get("/loan-requests?estado=RECHAZADA"), supervisor)
            .andExpect(jsonPath("$[0].idSolicitud").value(pending.id.toString()))
            .andExpect(jsonPath("$[0].motivoRechazo").value("Score insuficiente"))
    }

    @Test
    fun `el motivo es opcional (sin body o en blanco)`() {
        val first = request()
        reject(first.id!!, null).andExpect(status().isOk).andExpect(jsonPath("$.motivo").doesNotExist())
        assertNull(loanRequestRepository.findById(first.id!!).get().rejectionReason)

        val second = request()
        reject(second.id!!, """{"motivo":"   "}""").andExpect(status().isOk)
        assertNull(loanRequestRepository.findById(second.id!!).get().rejectionReason)
    }

    @Test
    fun `solo se rechaza una solicitud PENDIENTE (409) que exista (404)`() {
        val processed = request(LoanRequestStatus.PROCESADA)
        reject(processed.id!!, null).andExpect(status().isConflict)

        val pending = request()
        reject(pending.id!!, null).andExpect(status().isOk)
        reject(pending.id!!, null).andExpect(status().isConflict)

        reject(UUID.randomUUID(), null).andExpect(status().isNotFound)
    }

    @Test
    fun `motivo de mas de 255 caracteres 400`() {
        reject(request().id!!, """{"motivo":"${"x".repeat(256)}"}""").andExpect(status().isBadRequest)
    }

    @Test
    fun `solo el supervisor puede rechazar`() {
        val pending = request()
        reject(pending.id!!, null, authorization = null).andExpect(status().isUnauthorized)
        reject(pending.id!!, null, authorization = client).andExpect(status().isForbidden)
        assertEquals(LoanRequestStatus.PENDIENTE, loanRequestRepository.findById(pending.id!!).get().status)
    }

    // ---------- Inicio del cliente ----------

    @Test
    fun `el inicio del cliente muestra la solicitud rechazada con su motivo`() {
        val pending = request()
        reject(pending.id!!, """{"motivo":"Datos inconsistentes"}""")

        perform(get("/loans/current/phone/${user.phone}"), client)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tipo").value("SOLICITUD"))
            .andExpect(jsonPath("$.estado").value("RECHAZADA"))
            .andExpect(jsonPath("$.montoSolicitado").value(300))
            .andExpect(jsonPath("$.motivoRechazo").value("Datos inconsistentes"))
    }

    @Test
    fun `una solicitud nueva pendiente reemplaza a la rechazada en el inicio`() {
        request(LoanRequestStatus.RECHAZADA, createdAt = LoanRequest.now().minusSeconds(3600))
        request()

        perform(get("/loans/current/phone/${user.phone}"), client)
            .andExpect(jsonPath("$.estado").value("PENDIENTE"))
    }

    @Test
    fun `si la ultima solicitud no es la rechazada el inicio no la muestra`() {
        request(LoanRequestStatus.RECHAZADA, createdAt = LoanRequest.now().minusSeconds(3600))
        request(LoanRequestStatus.PROCESADA)

        perform(get("/loans/current/phone/${user.phone}"), client).andExpect(status().isNotFound)
    }

    // ---------- Con una solicitud rechazada no puede volver a solicitar ni llenar el cuestionario ----------

    @Test
    fun `con una solicitud rechazada no puede registrar otra solicitud (422)`() {
        reject(request().id!!, null)

        perform(
            post("/loan-requests/register").contentType(MediaType.APPLICATION_JSON).content(
                """{"idUsuario":"${user.id}","numCelular":"${user.phone}","tipoDeposito":"YAPE","categoria":"ORO",""" +
                    """"puntaje":720,"montoSolicitado":200,"frecuenciaCuota":15}"""
            ),
            client
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.message").value("Tu solicitud de préstamo fue rechazada: no puedes registrar otra solicitud"))
        assertEquals(1, loanRequestRepository.count())
    }

    @Test
    fun `con una solicitud rechazada no puede volver a llenar el cuestionario (422)`() {
        reject(request().id!!, null)

        perform(
            post("/questionnaires/register").contentType(MediaType.APPLICATION_JSON).content(
                """{"userId":"${user.id}","employmentType":"1h","employmentTenure":"2a","residentialTenure":"3a","housingType":"4b","dependents":"5a"}"""
            ),
            client
        ).andExpect(status().isUnprocessableEntity)
    }

    // ---------- Notificación ----------

    private fun notifications() = perform(get("/users/${user.id}/notifications"), client)

    @Test
    fun `el rechazo llega como notificacion con el motivo y deja de contar al marcarla como leida`() {
        val pending = request()
        reject(pending.id!!, """{"motivo":"Score insuficiente"}""")

        notifications()
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].type").value("LOAN_REQUEST_REJECTED"))
            .andExpect(jsonPath("$[0].message").value("Motivo: Score insuficiente"))
            .andExpect(jsonPath("$[0].referenceId").value(pending.id.toString()))

        markRead("LOAN_REQUEST_REJECTED", pending.id.toString()).andExpect(status().isNoContent)
        markRead("LOAN_REQUEST_REJECTED", pending.id.toString()).andExpect(status().isNoContent)

        notifications().andExpect(jsonPath("$.length()").value(0))
        // La card del inicio sigue mostrando la solicitud rechazada
        perform(get("/loans/current/phone/${user.phone}"), client).andExpect(jsonPath("$.estado").value("RECHAZADA"))
    }

    @Test
    fun `sin motivo la notificacion trae un mensaje generico`() {
        reject(request().id!!, null)
        notifications().andExpect(jsonPath("$[0].message").value("Revisamos tu solicitud y por ahora no podemos aprobarla."))
    }

    @Test
    fun `marcar como leida valida tipo, solicitud y dueno`() {
        val pending = request()
        reject(pending.id!!, null)

        markRead("DNI_PHOTOS_PENDING", null).andExpect(status().isBadRequest)
        markRead("LOAN_REQUEST_REJECTED", "no-es-uuid").andExpect(status().isBadRequest)
        markRead("LOAN_REQUEST_REJECTED", UUID.randomUUID().toString()).andExpect(status().isNotFound)

        // Solicitud de otro usuario
        val other = userRepository.save(User(phone = "912345678"))
        val foreign = loanRequestRepository.save(
            LoanRequest(
                userId = other.id!!, phone = other.phone, depositType = DepositType.PLIN, category = LoanCategory.PLATA,
                score = 600, requestedAmount = 100, status = LoanRequestStatus.RECHAZADA
            )
        )
        markRead("LOAN_REQUEST_REJECTED", foreign.id.toString()).andExpect(status().isNotFound)
        assertNull(loanRequestRepository.findById(foreign.id!!).get().rejectionSeenAt)
    }
}
