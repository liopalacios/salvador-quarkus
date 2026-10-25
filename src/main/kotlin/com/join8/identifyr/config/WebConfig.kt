package com.join8.identifyr.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@ConfigurationProperties(prefix = "cors")
data class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
    val allowedMethods: String = "GET,POST,PUT,DELETE,OPTIONS",
    val allowedHeaders: String = "*",
    val allowCredentials: Boolean = false,
    val maxAge: Long = 3600
)

@Configuration
@EnableConfigurationProperties(CorsProperties::class)
class WebConfig {
    // Lo usa SecurityConfig vía http.cors(); solo aplica a llamadas desde navegador
    @Bean
    fun corsConfigurationSource(props: CorsProperties): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOrigins = props.allowedOrigins
            allowedMethods = props.allowedMethods.split(",").map { it.trim() }
            allowedHeaders = props.allowedHeaders.split(",").map { it.trim() }
            allowCredentials = props.allowCredentials
            maxAge = props.maxAge
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}
