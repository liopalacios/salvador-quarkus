package com.join8.identifyr.controller

import com.join8.identifyr.repository.UserRepository
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserPhoneErrorTest {

    @Autowired lateinit var mockMvc: MockMvc
    @MockitoBean lateinit var userRepository: UserRepository

    // Con la BD caída la app debe reintentar: un 404 la llevaría a registrar a un usuario ya existente
    @Test
    fun `error interno devuelve 500 y no 404`() {
        `when`(userRepository.existsByPhone("987654321")).thenThrow(DataAccessResourceFailureException("BD caída"))

        mockMvc.perform(get("/users/phone/987654321"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.message").value("Ocurrió un error inesperado"))
    }
}
