package com.join8.identifyr.config

import com.join8.identifyr.exception.RateLimitExceededException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/** Límites por IP de las rutas públicas que revelan si un celular existe (prefijo "rate-limit" en application.yaml). */
@ConfigurationProperties(prefix = "rate-limit")
data class RateLimitProperties(
    val phoneLookup: Limit = Limit(maxRequests = 30),
    val userRegister: Limit = Limit(maxRequests = 10)
) {
    data class Limit(
        val maxRequests: Int,
        val windowSeconds: Long = 60,
        /** Tope de IPs en memoria: al superarlo, y sin ventanas vencidas que liberar, una IP nueva recibe 429. */
        val maxTrackedIps: Int = 100_000
    )
}

/**
 * Ventana fija por IP, en memoria: cada réplica de la API cuenta por separado,
 * así que el límite efectivo es maxRequests x réplicas.
 */
class IpRateLimiter(private val limit: RateLimitProperties.Limit, private val clock: Clock) {
    private class Window(val start: Long, var count: Int)

    private val windows = ConcurrentHashMap<String, Window>()
    private val windowMillis = limit.windowSeconds * 1000

    /** @throws RateLimitExceededException si la IP agotó su cupo en la ventana actual */
    fun acquire(ip: String) {
        val now = clock.millis()
        if (windows.size >= limit.maxTrackedIps && !windows.containsKey(ip)) {
            windows.values.removeIf { now - it.start >= windowMillis }
            if (windows.size >= limit.maxTrackedIps) throw RateLimitExceededException(limit.windowSeconds)
        }
        val window = windows.compute(ip) { _, current ->
            if (current == null || now - current.start >= windowMillis) Window(now, 1)
            else current.apply { count++ }
        }!!
        if (window.count > limit.maxRequests) {
            val remainingMillis = window.start + windowMillis - now
            throw RateLimitExceededException((remainingMillis + 999) / 1000)
        }
    }
}

/**
 * Corre antes del controlador, así que también cuenta las peticiones inválidas. Usa getRemoteAddr: en Azure
 * lo resuelve Tomcat con la IP real, no con un X-Forwarded-For enviado por el cliente (ver application-azure.yaml).
 * No registra la URL: en la consulta de celular contiene el número.
 *
 * @param httpMethod si se indica, solo cuenta ese método (GET /users/register corresponde a otra ruta: /users/{id})
 */
class IpRateLimitInterceptor(
    private val name: String,
    private val limiter: IpRateLimiter,
    private val httpMethod: String? = null
) : HandlerInterceptor {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (httpMethod != null && request.method != httpMethod) return true
        try {
            limiter.acquire(request.remoteAddr)
        } catch (ex: RateLimitExceededException) {
            log.warn("Rate limit {} excedido por ip={}", name, request.remoteAddr)
            throw ex
        }
        return true
    }
}

@Configuration
@EnableConfigurationProperties(RateLimitProperties::class)
class RateLimitConfig(
    private val props: RateLimitProperties,
    private val clock: Clock
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(IpRateLimitInterceptor("phone-lookup", IpRateLimiter(props.phoneLookup, clock)))
            .addPathPatterns("/users/phone/*")
        registry.addInterceptor(IpRateLimitInterceptor("user-register", IpRateLimiter(props.userRegister, clock), "POST"))
            .addPathPatterns("/users/register")
    }
}
