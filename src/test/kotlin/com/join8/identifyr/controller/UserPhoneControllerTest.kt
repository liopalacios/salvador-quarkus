package com.join8.identifyr.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.join8.identifyr.model.entity.User
import com.join8.identifyr.repository.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.net.URI
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension::class)
class UserPhoneControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var userRepository: UserRepository

    @AfterEach
    fun cleanUp() = userRepository.deleteAll()

    // URI ya codificada: con un String, MockMvc volvería a codificar los % de los casos inválidos
    private fun lookup(phone: String): ResultActions = mockMvc.perform(get(URI("/users/phone/$phone")))

    private fun saveUser(phone: String): User = userRepository.save(
        User(phone = phone, email = "secreto@example.com", photoFront = "f-${UUID.randomUUID()}",
            photoBack = "b-${UUID.randomUUID()}", passwordHash = "hash", address = "Av. Secreta 123")
    )

    private fun register(phone: String): ResultActions = mockMvc.perform(
        post("/users/register").contentType(MediaType.APPLICATION_JSON)
            .content("""{"phone":"$phone","dniFrontPhotoId":"f-${UUID.randomUUID()}","dniBackPhotoId":"b-${UUID.randomUUID()}"}""")
    )

    @Test
    fun `numero registrado devuelve 200 con exists true sin token`() {
        saveUser("987654321")

        lookup("987654321").andExpect(status().isOk).andExpect(jsonPath("$.exists").value(true))
    }

    @Test
    fun `numero no registrado devuelve 404 con exists false`() {
        saveUser("987654321")

        lookup("000000000").andExpect(status().isNotFound).andExpect(jsonPath("$.exists").value(false))
    }

    @Test
    fun `formatos invalidos devuelven 400 con el formato de error de la API`() {
        val invalid = listOf("98765432", "9876543210", "abcdefghi", "98765432a", "+51987654321", "987%20654321", "%D9%A9%D9%A8%D9%A7%D9%A6%D9%A5%D9%A4%D9%A3%D9%A2%D9%A1")
        invalid.forEach { phone ->
            lookup(phone)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Celular debe tener 9 dígitos numéricos"))
                .andExpect(jsonPath("$.timestamp").exists())
        }
    }

    @Test
    fun `numero registrado mediante POST register se encuentra`() {
        lookup("912345678").andExpect(status().isNotFound)

        register("912345678")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.phone").value("912345678"))
            .andExpect(jsonPath("$.id").exists())

        lookup("912345678").andExpect(status().isOk).andExpect(jsonPath("$.exists").value(true))
    }

    @Test
    fun `POST register sigue rechazando celular duplicado y formato invalido`() {
        register("912345678").andExpect(status().isCreated)

        register("912345678").andExpect(status().isBadRequest)
        register("9123").andExpect(status().isBadRequest)
    }

    @Test
    fun `la respuesta solo contiene exists y ningun dato del usuario`() {
        val user = saveUser("987654321")

        listOf("987654321" to 200, "000000000" to 404).forEach { (phone, code) ->
            val body = lookup(phone).andExpect(status().`is`(code)).andReturn().response.contentAsString

            assertEquals(setOf("exists"), objectMapper.readTree(body).fieldNames().asSequence().toSet())
            listOf(user.id.toString(), user.email, user.address!!, user.photoFront, phone).forEach {
                assertFalse(body.contains(it), "la respuesta filtra $it")
            }
        }
    }

    @Test
    fun `la ruta no choca con GET users id`() {
        val user = saveUser("987654321")

        mockMvc.perform(get("/users/${user.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(user.id.toString()))
        mockMvc.perform(get("/users/phone")).andExpect(status().isBadRequest)
    }

    @Test
    fun `los logs solo muestran el numero enmascarado`(output: CapturedOutput) {
        saveUser("987654321")

        lookup("987654321").andExpect(status().isOk)
        lookup("912345678").andExpect(status().isNotFound)
        lookup("91234567a").andExpect(status().isBadRequest)

        assertTrue(output.out.contains("987***321"))
        assertTrue(output.out.contains("912***678"))
        listOf("987654321", "912345678", "91234567a").forEach {
            assertFalse(output.toString().contains(it), "el log contiene $it")
        }
    }
}
