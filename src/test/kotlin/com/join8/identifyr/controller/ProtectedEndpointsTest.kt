package com.join8.identifyr.controller

import com.join8.identifyr.model.entity.User
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Filtro global: GET / POST / PUT / DELETE exigen token; autorización por dueño del recurso y por rol. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProtectedEndpointsTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var questionnaireRepository: com.join8.identifyr.repository.QuestionnaireRepository
    @Value("\${jwt.secret}") lateinit var jwtSecret: String

    private lateinit var user: User
    private lateinit var other: User

    @BeforeEach
    fun createUsers() {
        user = userRepository.save(User(phone = "987654321"))
        other = userRepository.save(User(phone = "912345678"))
    }

    @AfterEach
    fun cleanUp() {
        questionnaireRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun bearer(u: User) = "Bearer ${TestTokens.user(jwtSecret, u.id!!, u.phone)}"
    private val expired get() = "Bearer ${TestTokens.create(jwtSecret, user.id!!, user.phone, expiresAt = Instant.now().minusSeconds(120))}"
    private val badSignature get() = "Bearer ${TestTokens.create("otra-clave-".repeat(5), user.id!!, user.phone)}"

    private fun perform(request: MockHttpServletRequestBuilder, authorization: String?): ResultActions =
        mockMvc.perform(request.apply { authorization?.let { header(HttpHeaders.AUTHORIZATION, it) } })

    private val photosJson = """{"dniFrontPhotoId":"front-1","dniBackPhotoId":"back-1"}"""
    private fun putPhotos(userId: UUID) = put("/users/$userId/dni-photos").contentType(MediaType.APPLICATION_JSON).content(photosJson)
    private fun getUser(userId: UUID) = get("/users/$userId")
    // POST con el userId en el body (cuestionario)
    private fun postQuestionnaire(userId: UUID) = post("/questionnaires/register").contentType(MediaType.APPLICATION_JSON)
        .content("""{"userId":"$userId","employmentType":"1h","employmentTenure":"2a","residentialTenure":"3a","housingType":"4b","dependents":"5a"}""")

    @Test
    fun `GET POST PUT DELETE sin token devuelven 401 con el formato de error`() {
        listOf(getUser(user.id!!), postQuestionnaire(user.id!!), putPhotos(user.id!!), delete("/users/${user.id}")).forEach {
            perform(it, null)
                .andExpect(status().isUnauthorized)
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").isString)
                .andExpect(jsonPath("$.status").value(401))
        }
    }

    @Test
    fun `token expirado, con otra firma o basura devuelve 401`() {
        listOf(expired, badSignature, "Bearer no-es-un-jwt", "Bearer null", "Basic dXNlcjpwYXNz").forEach { auth ->
            listOf(getUser(user.id!!), postQuestionnaire(user.id!!), putPhotos(user.id!!), delete("/users/${user.id}")).forEach {
                perform(it, auth).andExpect(status().isUnauthorized).andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
            }
        }
    }

    @Test
    fun `token sin sub devuelve 401`() {
        val noSub = TestTokens.create(jwtSecret, "", user.phone)
        perform(getUser(user.id!!), "Bearer $noSub").andExpect(status().isUnauthorized)
    }

    @Test
    fun `token de otro usuario devuelve 403`() {
        listOf(getUser(user.id!!), postQuestionnaire(user.id!!), putPhotos(user.id!!), get("/scores/${user.id}"), get("/context/${user.id}"))
            .forEach {
                perform(it, bearer(other))
                    .andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"))
            }
        assertNull(userRepository.findById(user.id!!).get().photoFront)
    }

    @Test
    fun `token correcto devuelve 2xx`() {
        perform(getUser(user.id!!), bearer(user)).andExpect(status().isOk).andExpect(jsonPath("$.id").value(user.id.toString()))
        perform(postQuestionnaire(user.id!!), bearer(user)).andExpect(status().isCreated)
        perform(putPhotos(user.id!!), bearer(user))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(user.id.toString()))
            .andExpect(jsonPath("$.dniFrontPhotoId").value("front-1"))
            .andExpect(jsonPath("$.dniBackPhotoId").value("back-1"))
    }

    @Test
    fun `DELETE con token valido pasa el filtro (no hay endpoints DELETE todavia)`() {
        perform(delete("/users/${user.id}"), bearer(user)).andExpect(status().isMethodNotAllowed)
    }

    @Test
    fun `PUT dni-photos guarda las fotos, valida el body y rechaza fotos de otro usuario`() {
        perform(putPhotos(user.id!!), bearer(user)).andExpect(status().isOk)
        val saved = userRepository.findById(user.id!!).get()
        assertEquals("front-1", saved.photoFront)
        assertEquals("back-1", saved.photoBack)

        perform(putPhotos(other.id!!), bearer(other)).andExpect(status().isConflict)
        perform(
            put("/users/${user.id}/dni-photos").contentType(MediaType.APPLICATION_JSON).content("""{"dniFrontPhotoId":"x"}"""),
            bearer(user)
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `endpoints de supervisor con token USER devuelven 403`() {
        perform(get("/loan-requests?estado=PENDIENTE"), bearer(user))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("FORBIDDEN"))
        perform(
            post("/loans/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"idSolicitud":"${UUID.randomUUID()}","codigoOperacion":"OP-1"}"""),
            bearer(user)
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `supervisor puede leer recursos de un usuario pero no escribir en su nombre`() {
        val supervisor = "Bearer ${TestTokens.supervisor(jwtSecret)}"
        perform(getUser(user.id!!), supervisor).andExpect(status().isOk)
        perform(putPhotos(user.id!!), supervisor).andExpect(status().isForbidden)
        perform(postQuestionnaire(user.id!!), supervisor).andExpect(status().isForbidden)
    }

    @Test
    fun `los endpoints publicos no exigen token`() {
        mockMvc.perform(get("/users/phone/987654321")).andExpect(status().isOk)
        mockMvc.perform(post("/users/register").contentType(MediaType.APPLICATION_JSON).content("""{"phone":"955555555"}"""))
            .andExpect(status().isCreated)
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""{"phone":"955555555","password":"1234"}"""))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk)
    }
}
