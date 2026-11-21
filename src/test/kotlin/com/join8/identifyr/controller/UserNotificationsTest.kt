package com.join8.identifyr.controller

import com.join8.identifyr.model.entity.User
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
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.*
import kotlin.test.Test

/** GET /users/{userId}/notifications: notificaciones calculadas a partir de los datos del usuario. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserNotificationsTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Value("\${jwt.secret}") lateinit var jwtSecret: String

    @AfterEach
    fun cleanUp() = userRepository.deleteAll()

    private fun bearer(u: User) = "Bearer ${TestTokens.user(jwtSecret, u.id!!, u.phone)}"

    private fun notifications(userId: UUID, authorization: String?): ResultActions =
        mockMvc.perform(get("/users/$userId/notifications").apply { authorization?.let { header(HttpHeaders.AUTHORIZATION, it) } })

    @Test
    fun `sin fotos del DNI devuelve la notificacion DNI_PHOTOS_PENDING`() {
        val user = userRepository.save(User(phone = "987654321"))
        notifications(user.id!!, bearer(user))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].type").value("DNI_PHOTOS_PENDING"))
            .andExpect(jsonPath("$[0].title").isNotEmpty)
            .andExpect(jsonPath("$[0].message").isNotEmpty)
    }

    @Test
    fun `con una sola foto o una foto vacia sigue pendiente`() {
        val user = userRepository.save(User(phone = "987654321", photoFront = "f-1", photoBack = ""))
        notifications(user.id!!, bearer(user)).andExpect(jsonPath("$[0].type").value("DNI_PHOTOS_PENDING"))
    }

    @Test
    fun `con ambas fotos devuelve la lista vacia`() {
        val user = userRepository.save(User(phone = "987654321", photoFront = "f-1", photoBack = "b-1"))
        notifications(user.id!!, bearer(user))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `sin token 401, con token de otro usuario 403 y usuario inexistente 404`() {
        val user = userRepository.save(User(phone = "987654321"))
        val other = userRepository.save(User(phone = "912345678"))
        notifications(user.id!!, null).andExpect(status().isUnauthorized)
        notifications(user.id!!, bearer(other)).andExpect(status().isForbidden)

        val ghost = User(id = UUID.randomUUID(), phone = "900000001")
        notifications(ghost.id!!, bearer(ghost)).andExpect(status().isNotFound)
    }
}
