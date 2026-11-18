package com.join8.identifyr.controller

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.*
import kotlin.test.Test

/** Rate limit por IP de login y register-password (10/min por defecto; aquí 3 para el test). */
@SpringBootTest(properties = ["rate-limit.auth-login.max-requests=3", "rate-limit.register-password.max-requests=3"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRateLimitTest {

    @Autowired lateinit var mockMvc: MockMvc

    private fun post(path: String, json: String, ip: String) = mockMvc.perform(
        post(path).contentType(MediaType.APPLICATION_JSON).content(json).with { it.apply { remoteAddr = ip } }
    )

    @Test
    fun `login y supervisor login comparten el cupo y al excederlo devuelven 429`() {
        val body = """{"phone":"900000000","password":"1234"}"""
        repeat(2) { post("/auth/login", body, "198.51.100.1").andExpect(status().isUnauthorized) }
        post("/auth/supervisor/login", body, "198.51.100.1").andExpect(status().isUnauthorized)

        post("/auth/login", body, "198.51.100.1")
            .andExpect(status().isTooManyRequests)
            .andExpect(header().exists("Retry-After"))
        post("/auth/login", body, "198.51.100.2").andExpect(status().isUnauthorized)
    }

    @Test
    fun `staff login comparte el cupo de login`() {
        val body = """{"phone":"900000000","password":"1234"}"""
        repeat(3) { post("/auth/login", body, "198.51.100.4").andExpect(status().isUnauthorized) }
        post("/auth/staff/login", """{"username":"nadie","password":"x"}""", "198.51.100.4")
            .andExpect(status().isTooManyRequests)
    }

    @Test
    fun `register-password al exceder el limite devuelve 429`() {
        val body = """{"userId":"${UUID.randomUUID()}","password":"1234"}"""
        repeat(3) { post("/auth/register-password", body, "198.51.100.3").andExpect(status().isNotFound) }
        post("/auth/register-password", body, "198.51.100.3").andExpect(status().isTooManyRequests)
    }
}
