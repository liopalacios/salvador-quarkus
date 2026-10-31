package com.join8.identifyr.controller

import com.join8.identifyr.model.entity.User
import com.join8.identifyr.repository.QuestionnaireRepository
import com.join8.identifyr.repository.UserRepository
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuestionnaireControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var questionnaireRepository: QuestionnaireRepository

    private lateinit var userId: UUID

    @BeforeEach
    fun createUser() {
        userId = userRepository.save(
            User(phone = "987654321", email = "", photoFront = "f-${UUID.randomUUID()}",
                photoBack = "b-${UUID.randomUUID()}", passwordHash = "")
        ).id!!
    }

    @AfterEach
    fun cleanUp() {
        questionnaireRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun register(json: String): ResultActions =
        mockMvc.perform(post("/questionnaires/register").contentType(MediaType.APPLICATION_JSON).content(json))

    private val block1 get() = """
        {"userId":"$userId","employmentType":"1h","employmentTenure":"2a",
         "residentialTenure":"3a","housingType":"4b","dependents":"5a"}
    """

    @Test
    fun `flujo completo registro - score - perfil - score`() {
        register(block1)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.questionnaireId").isNumber)
            .andExpect(jsonPath("$.userId").value(userId.toString()))
            .andExpect(jsonPath("$.status").value("A"))
            .andExpect(jsonPath("$.createdAt").isString)

        // 35*.21 + 0 + 0 + 85*.105 + 100*.14 = 30.275 -> 30
        mockMvc.perform(get("/scores/$userId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.scoreValue").value(30))
            .andExpect(jsonPath("$.levelName").value("PLATA"))
            .andExpect(jsonPath("$.maxLoanAmount").value(300))
            .andExpect(jsonPath("$.reasons").isArray)

        register("""{"userId":"$userId","email":"ana@correo.com","educationLevel":"Técnico","workApps":"Rappi, Uber"}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("A"))

        // 30.275 + (5 + 4 + 10) * 0.3 = 30.275 + 5.7 = 35.975 -> 36
        mockMvc.perform(get("/scores/$userId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.scoreValue").value(36))
            .andExpect(jsonPath("$.levelName").value("PLATA"))
            .andExpect(jsonPath("$.reasons", hasItem("Agregaste tu nivel de estudios (+4)")))

        assertEquals(listOf("I", "A"), questionnaireRepository.findAllByUserIdOrderByIdAsc(userId).map { it.estado })
    }

    @Test
    fun `validaciones devuelven 400`() {
        val invalid = listOf(
            """{"employmentType":"1h"}""",                                           // sin userId
            """{"userId":"no-es-uuid","email":"a@b.com"}""",                          // userId mal formado
            """{"userId":"$userId"}""",                                               // solo userId
            """{"userId":"$userId","email":"  "}""",                                  // solo campos vacíos
            """{"userId":"$userId","employmentType":"1h"}""",                         // primer registro incompleto
            block1.replace("\"1h\"", "\"1z\""),                                       // código inválido
            block1.replace("\"5a\"", "\"5a\",\"email\":\"sin-arroba\""),              // email inválido
            block1.replace("\"5a\"", "\"5a\",\"ruc\":\"123456789\""),                 // RUC corto
            block1.replace("\"5a\"", "\"5a\",\"ruc\":\"2012345678A\""),               // RUC no numérico
            block1.replace("\"5a\"", "\"5a\",\"educationLevel\":\"Doctorado\"")       // nivel fuera de la lista
        )
        invalid.forEach { json ->
            register(json).andExpect(status().isBadRequest)
        }
        assertEquals(0, questionnaireRepository.count())
    }

    @Test
    fun `RUC invalido en segundo envio devuelve 400 con el mensaje`() {
        register(block1).andExpect(status().isCreated)
        register("""{"userId":"$userId","ruc":"123"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("El RUC debe tener 11 dígitos"))
    }

    @Test
    fun `usuario inexistente devuelve 404 en registro y en score`() {
        val unknown = UUID.randomUUID()
        register(block1.replace(userId.toString(), unknown.toString())).andExpect(status().isNotFound)
        mockMvc.perform(get("/scores/$unknown")).andExpect(status().isNotFound)
    }

    @Test
    fun `score sin registro activo devuelve 404`() {
        mockMvc.perform(get("/scores/$userId")).andExpect(status().isNotFound)
    }
}
