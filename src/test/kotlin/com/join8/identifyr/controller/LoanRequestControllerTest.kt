package com.join8.identifyr.controller

import com.join8.identifyr.model.entity.DepositType
import com.join8.identifyr.model.entity.LoanCategory
import com.join8.identifyr.model.entity.LoanRequestStatus
import com.join8.identifyr.model.entity.User
import com.join8.identifyr.repository.LoanRequestRepository
import com.join8.identifyr.repository.UserRepository
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import com.join8.identifyr.support.TestTokens
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoanRequestControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var loanRequestRepository: LoanRequestRepository
    @Value("\${jwt.secret}") lateinit var jwtSecret: String

    private lateinit var userId: UUID

    @BeforeEach
    fun createUser() {
        userId = userRepository.save(
            User(phone = "987654321")
        ).id!!
    }

    @AfterEach
    fun cleanUp() {
        loanRequestRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun bearer(id: UUID = userId, phone: String = "987654321") = "Bearer ${TestTokens.user(jwtSecret, id, phone)}"

    private fun register(json: String, authorization: String? = bearer()): ResultActions = mockMvc.perform(
        post("/loan-requests/register").contentType(MediaType.APPLICATION_JSON).content(json)
            .apply { authorization?.let { header(HttpHeaders.AUTHORIZATION, it) } }
    )

    private fun body(
        idUsuario: Any? = userId,
        numCelular: Any? = "987654321",
        tipoDeposito: Any? = "YAPE",
        categoria: Any? = "ORO",
        puntaje: Any? = 720,
        montoSolicitado: Any? = 200
    ): String {
        fun json(v: Any?) = when (v) {
            null -> "null"
            is Number -> v.toString()
            else -> "\"$v\""
        }
        return """
            {"idUsuario":${json(idUsuario)},"numCelular":${json(numCelular)},"tipoDeposito":${json(tipoDeposito)},
             "categoria":${json(categoria)},"puntaje":${json(puntaje)},"montoSolicitado":${json(montoSolicitado)}}
        """
    }

    @Test
    fun `registro valido devuelve 201 y guarda la solicitud PENDIENTE sin codigo de operacion`() {
        register(body())
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isString)
            .andExpect(jsonPath("$.status").value("PENDIENTE"))
            .andExpect(jsonPath("$.createdAt").isString)

        val saved = loanRequestRepository.findAllByUserId(userId).single()
        assertEquals(LoanRequestStatus.PENDIENTE, saved.status)
        assertNull(saved.operationCode)
        assertEquals("987654321", saved.phone)
        assertEquals(DepositType.YAPE, saved.depositType)
        assertEquals(LoanCategory.ORO, saved.category)
        assertEquals(720, saved.score)
        assertEquals(200, saved.requestedAmount)
    }

    @Test
    fun `createdAt de la respuesta es ISO-8601 y coincide con el id guardado`() {
        val response = register(body(tipoDeposito = "PLIN")).andExpect(status().isCreated).andReturn().response
        val id = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(response.contentAsString)!!.groupValues[1]
        val createdAt = Regex("\"createdAt\"\\s*:\\s*\"([^\"]+)\"").find(response.contentAsString)!!.groupValues[1]

        val saved = loanRequestRepository.findById(UUID.fromString(id)).get()
        assertEquals(saved.createdAt, Instant.parse(createdAt))
        assertEquals(DepositType.PLIN, saved.depositType)
    }

    @Test
    fun `tipoDeposito distinto de YAPE o PLIN devuelve 400`() {
        listOf("BCP", "yape", "", "TRANSFERENCIA").forEach {
            register(body(tipoDeposito = it))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message", containsString("tipoDeposito")))
        }
        assertEquals(0, loanRequestRepository.count())
    }

    @Test
    fun `categoria distinta de ORO PLATA o BRONCE devuelve 400`() {
        listOf("DIAMANTE", "oro", "Plata", "").forEach {
            register(body(categoria = it))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message", containsString("categoria")))
        }
        assertEquals(0, loanRequestRepository.count())
    }

    @Test
    fun `montoSolicitado menor o igual a 0 o puntaje negativo devuelve 400`() {
        register(body(montoSolicitado = 0))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message", containsString("montoSolicitado: debe ser mayor que 0")))
        register(body(montoSolicitado = -50))
            .andExpect(status().isBadRequest)
        register(body(puntaje = -1))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message", containsString("puntaje: debe ser mayor o igual a 0")))
        assertEquals(0, loanRequestRepository.count())
    }

    @Test
    fun `puntaje 0 es valido`() {
        register(body(categoria = "BRONCE", puntaje = 0, montoSolicitado = 50)).andExpect(status().isCreated)
    }

    @Test
    fun `montoSolicitado mayor al maximo de la categoria devuelve 400`() {
        register(body(categoria = "BRONCE", montoSolicitado = 51))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message", containsString("no puede superar S/ 50")))
        register(body(categoria = "PLATA", montoSolicitado = 301)).andExpect(status().isBadRequest)
        register(body(categoria = "ORO", montoSolicitado = 451)).andExpect(status().isBadRequest)
        assertEquals(0, loanRequestRepository.count())

        register(body(categoria = "PLATA", montoSolicitado = 300)).andExpect(status().isCreated)
    }

    @Test
    fun `celular invalido, idUsuario mal formado, campos faltantes o JSON roto devuelven 400`() {
        listOf(
            body(numCelular = "887654321"),     // no empieza con 9
            body(numCelular = "98765432"),      // 8 dígitos
            body(numCelular = "98765432a"),     // no numérico
            body(idUsuario = "no-es-uuid"),
            body(idUsuario = null),
            body(numCelular = null),
            body(puntaje = null),
            body(montoSolicitado = null),
            body(puntaje = "abc"),              // tipo incorrecto
            "{}",
            "{no es json"
        ).forEach { json ->
            register(json).andExpect(status().isBadRequest)
        }
        assertEquals(0, loanRequestRepository.count())
    }

    @Test
    fun `body vacio informa cada campo faltante`() {
        register("{}")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message", allOf(
                containsString("idUsuario: es obligatorio"),
                containsString("numCelular: es obligatorio"),
                containsString("tipoDeposito: es obligatorio"),
                containsString("categoria: es obligatorio"),
                containsString("puntaje: es obligatorio"),
                containsString("montoSolicitado: es obligatorio")
            )))
    }

    @Test
    fun `idUsuario inexistente devuelve 422 y nunca 404`() {
        val unknown = UUID.randomUUID()
        register(body(idUsuario = unknown), bearer(unknown))
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.message", containsString("no existe")))
        assertEquals(0, loanRequestRepository.count())
    }

    @Test
    fun `sin token 401 y con idUsuario o celular de otro usuario 403`() {
        register(body(), authorization = null).andExpect(status().isUnauthorized)
        register(body(), bearer(id = UUID.randomUUID())).andExpect(status().isForbidden)
        register(body(), bearer(phone = "911111111")).andExpect(status().isForbidden)
        register(body(), "Bearer ${TestTokens.supervisor(jwtSecret)}").andExpect(status().isForbidden)
        assertEquals(0, loanRequestRepository.count())
    }

    @Test
    fun `segunda solicitud con una PENDIENTE activa devuelve 422`() {
        register(body()).andExpect(status().isCreated)
        register(body(tipoDeposito = "PLIN", montoSolicitado = 100))
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.message", containsString("pendiente")))
        assertEquals(1, loanRequestRepository.findAllByUserId(userId).size)
    }

    @Test
    fun `nueva solicitud permitida cuando la anterior ya no esta PENDIENTE`() {
        register(body()).andExpect(status().isCreated)
        val previous = loanRequestRepository.findAllByUserId(userId).single()
        previous.status = LoanRequestStatus.PROCESADA
        previous.operationCode = "OP-123"
        loanRequestRepository.save(previous)

        register(body()).andExpect(status().isCreated)
        assertEquals(2, loanRequestRepository.findAllByUserId(userId).size)
    }

    @Test
    fun `las claves JSON del request son exactamente las que envia la app`() {
        // Claves exactas -> 201
        register(
            """{"idUsuario":"$userId","numCelular":"987654321","tipoDeposito":"YAPE",
                "categoria":"ORO","puntaje":720,"montoSolicitado":200}"""
        ).andExpect(status().isCreated)
        loanRequestRepository.deleteAll()

        // Mismos datos con otros nombres (inglés, snake_case, mayúsculas) -> no se reconocen -> 400
        listOf(
            """{"userId":"$userId","phone":"987654321","depositType":"YAPE",
                "category":"ORO","score":720,"requestedAmount":200}""",
            """{"id_usuario":"$userId","num_celular":"987654321","tipo_deposito":"YAPE",
                "categoria":"ORO","puntaje":720,"monto_solicitado":200}""",
            """{"IdUsuario":"$userId","NumCelular":"987654321","TipoDeposito":"YAPE",
                "Categoria":"ORO","Puntaje":720,"MontoSolicitado":200}"""
        ).forEach { json ->
            register(json).andExpect(status().isBadRequest)
        }
        assertEquals(0, loanRequestRepository.count())
    }

    @Test
    fun `frecuenciaCuota 15 o 30 se guarda, otro valor devuelve 400 y sin ella queda null`() {
        val withFreq = body().trim().removeSuffix("}") + ""","frecuenciaCuota":30}"""
        register(withFreq).andExpect(status().isCreated)
        assertEquals(30, loanRequestRepository.findAllByUserId(userId).single().installmentFrequency)

        loanRequestRepository.deleteAll()
        register(body().trim().removeSuffix("}") + ""","frecuenciaCuota":7}""").andExpect(status().isBadRequest)
        assertEquals(0, loanRequestRepository.count())

        register(body()).andExpect(status().isCreated)
        assertEquals(null, loanRequestRepository.findAllByUserId(userId).single().installmentFrequency)
    }
}
