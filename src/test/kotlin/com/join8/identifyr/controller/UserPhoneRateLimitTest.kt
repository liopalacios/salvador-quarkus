package com.join8.identifyr.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.join8.identifyr.config.IpRateLimiter
import com.join8.identifyr.config.RateLimitProperties
import com.join8.identifyr.exception.RateLimitExceededException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Tomcat real con el application-azure.yaml real: comprueba que la IP la decide el último salto de
 * X-Forwarded-For (el del ingress) y que rotar lo que envía el cliente no evade el límite.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["rate-limit.phone-lookup.max-requests=3", "rate-limit.user-register.max-requests=2", "JWT_SECRET=0123456789abcdef0123456789abcdef"]
)
@ActiveProfiles("azure", "test")
class UserPhoneRateLimitTest {

    @LocalServerPort var port: Int = 0
    @Autowired lateinit var objectMapper: ObjectMapper

    private val client = HttpClient.newHttpClient()

    private fun lookup(forwardedFor: String, path: String = "/users/phone/000000000"): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port/api/v1$path"))
                .header("X-Forwarded-For", forwardedFor).build(),
            HttpResponse.BodyHandlers.ofString()
        )

    @Test
    fun `al exceder el limite devuelve 429 con el formato de error y Retry-After`() {
        repeat(3) { assertEquals(404, lookup("203.0.113.1").statusCode()) }

        val blocked = lookup("203.0.113.1")

        assertEquals(429, blocked.statusCode())
        val body = objectMapper.readTree(blocked.body())
        assertEquals(429, body["status"].asInt())
        assertEquals("Too Many Requests", body["error"].asText())
        assertNotNull(body["message"].asText())
        assertNotNull(blocked.headers().firstValue("Retry-After").orElse(null))
    }

    @Test
    fun `rotar el X-Forwarded-For enviado por el cliente no evade el limite`() {
        repeat(3) { i -> assertEquals(404, lookup("198.18.0.$i, 203.0.113.2").statusCode()) }

        assertEquals(429, lookup("198.18.0.99, 203.0.113.2").statusCode())
    }

    @Test
    fun `el limite es por IP y tambien cuenta los formatos invalidos`() {
        repeat(3) { assertEquals(400, lookup("203.0.113.3", "/users/phone/abc").statusCode()) }

        assertEquals(429, lookup("203.0.113.3").statusCode())
        assertEquals(404, lookup("203.0.113.4").statusCode())
    }

    @Test
    fun `el limite no afecta a otras rutas de usuarios`() {
        repeat(4) { lookup("203.0.113.5") }

        // /users/{id} con un id no UUID: 400 de esa ruta, no 429
        assertEquals(400, lookup("203.0.113.5", "/users/no-es-uuid").statusCode())
    }

    // Body vacío: responde 400 sin crear usuarios, pero la petición cuenta igual
    private fun register(forwardedFor: String): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/users/register"))
                .header("X-Forwarded-For", forwardedFor).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
            HttpResponse.BodyHandlers.ofString()
        )

    @Test
    fun `POST register devuelve 429 con el formato de error al exceder su limite`() {
        repeat(2) { assertEquals(400, register("203.0.113.6").statusCode()) }

        val blocked = register("203.0.113.6")

        assertEquals(429, blocked.statusCode())
        assertEquals("Too Many Requests", objectMapper.readTree(blocked.body())["error"].asText())
        assertNotNull(blocked.headers().firstValue("Retry-After").orElse(null))
        assertEquals(400, register("203.0.113.7").statusCode())
    }

    @Test
    fun `rotar el X-Forwarded-For no evade el limite de POST register`() {
        repeat(2) { i -> assertEquals(400, register("198.18.1.$i, 203.0.113.8").statusCode()) }

        assertEquals(429, register("198.18.1.99, 203.0.113.8").statusCode())
    }

    @Test
    fun `los contadores de registro y consulta son independientes`() {
        repeat(3) { register("203.0.113.9") }
        assertEquals(429, register("203.0.113.9").statusCode())
        assertEquals(404, lookup("203.0.113.9").statusCode())

        repeat(4) { lookup("203.0.113.10") }
        assertEquals(429, lookup("203.0.113.10").statusCode())
        assertEquals(400, register("203.0.113.10").statusCode())
    }

    @Test
    fun `GET users register no consume el cupo del registro`() {
        repeat(3) { assertEquals(400, lookup("203.0.113.11", "/users/register").statusCode()) }

        assertEquals(400, register("203.0.113.11").statusCode())
    }
}

class IpRateLimiterTest {

    private class MutableClock(var instant: Instant) : Clock() {
        override fun instant() = instant
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
    }

    private val clock = MutableClock(Instant.parse("2026-11-03T10:00:00Z"))

    private fun limiter(maxTrackedIps: Int = 100) =
        IpRateLimiter(RateLimitProperties.Limit(maxRequests = 2, windowSeconds = 60, maxTrackedIps = maxTrackedIps), clock)

    @Test
    fun `el cupo se renueva al vencer la ventana e informa los segundos restantes`() {
        val limiter = limiter()
        repeat(2) { limiter.acquire("ip") }

        clock.instant = clock.instant.plusSeconds(20)
        assertEquals(40, assertFailsWith<RateLimitExceededException> { limiter.acquire("ip") }.retryAfterSeconds)

        clock.instant = clock.instant.plusSeconds(40)
        limiter.acquire("ip")
    }

    @Test
    fun `la memoria queda acotada - libera ventanas vencidas y si no puede rechaza IPs nuevas`() {
        val limiter = limiter(maxTrackedIps = 2)
        limiter.acquire("a")
        limiter.acquire("b")

        assertFailsWith<RateLimitExceededException> { limiter.acquire("c") }
        limiter.acquire("a")

        clock.instant = clock.instant.plusSeconds(60)
        limiter.acquire("c")
    }
}
