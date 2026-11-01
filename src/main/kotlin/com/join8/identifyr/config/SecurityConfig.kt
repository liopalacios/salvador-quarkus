package com.join8.identifyr.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.authorization.AuthenticatedAuthorizationManager
import org.springframework.security.authorization.AuthorityAuthorizationManager
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.authorization.AuthorizationManagers
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver
import org.springframework.security.web.access.intercept.RequestAuthorizationContext
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain
import javax.crypto.spec.SecretKeySpec

@Configuration
@EnableWebSecurity
class SecurityConfig {

    companion object {
        const val ROLE_SUPERVISOR = "SUPERVISOR"
    }

    @Bean
    fun passwordEncoder(): BCryptPasswordEncoder = BCryptPasswordEncoder()

    /**
     * Tokens Bearer JWT firmados con HS256; la clave son los bytes UTF-8 de jwt.secret (JWT_SECRET).
     * Los roles van en el claim "roles" (ej. ["SUPERVISOR"]) y se mapean a ROLE_SUPERVISOR.
     */
    @Bean
    fun jwtDecoder(@Value("\${jwt.secret}") secret: String): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            .macAlgorithm(MacAlgorithm.HS256)
            .build()

    private fun jwtAuthenticationConverter() = JwtAuthenticationConverter().apply {
        setJwtGrantedAuthoritiesConverter(JwtGrantedAuthoritiesConverter().apply {
            setAuthoritiesClaimName("roles")
            setAuthorityPrefix("ROLE_")
        })
    }

    /**
     * Header Authorization sin token ("Bearer", "Bearer   ", "Bearer null") = sin token: la petición
     * sigue como anónima en vez de 401. Un token presente se valida siempre (inválido o vencido -> 401).
     */
    private fun bearerTokenResolver(): BearerTokenResolver {
        val delegate = DefaultBearerTokenResolver()
        return BearerTokenResolver { request ->
            val header = request.getHeader(HttpHeaders.AUTHORIZATION)?.trim()
            val token = header?.takeIf { it.startsWith("bearer", ignoreCase = true) }?.substring(6)?.trim()
            if (token != null && (token.isEmpty() || token.equals("null", ignoreCase = true))) null
            else delegate.resolve(request)
        }
    }

    /**
     * Endpoints del supervisor. Con allowAnonymous=true (temporal, mientras la app no tenga login de
     * supervisor) se permite sin token; con token, igual se exige el rol SUPERVISOR.
     */
    private fun supervisorAccess(allowAnonymous: Boolean): AuthorizationManager<RequestAuthorizationContext> {
        val supervisor = AuthorityAuthorizationManager.hasRole<RequestAuthorizationContext>(ROLE_SUPERVISOR)
        return if (allowAnonymous) AuthorizationManagers.anyOf(AuthenticatedAuthorizationManager.anonymous(), supervisor)
        else supervisor
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        @Value("\${security.supervisor.allow-anonymous:false}") allowAnonymousSupervisor: Boolean
    ): SecurityFilterChain {
        return http
            .csrf{ it.disable() }
            .cors{ }
            .authorizeHttpRequests { auth ->
                auth
                    // Registro de solicitudes: lo usa la app del cliente, sin token
                    .requestMatchers(HttpMethod.POST, "/loan-requests/register").permitAll()
                    // Consulta de solicitudes: expone celulares, solo supervisor (ver supervisorAccess)
                    .requestMatchers(HttpMethod.GET, "/loan-requests", "/loan-requests/**")
                    .access(supervisorAccess(allowAnonymousSupervisor))
                    // Registro de préstamos (desembolso): solo supervisor
                    .requestMatchers(HttpMethod.POST, "/loans/register")
                    .access(supervisorAccess(allowAnonymousSupervisor))
                    .requestMatchers(
                        "/users/register",
                        "/users/**",
                        "/context/**",
                        "/stability/**",
                        "/scores/**",
                        "/questionnaires/**",
                        "/actuator/health",
                        "/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer {
                it.bearerTokenResolver(bearerTokenResolver())
                it.jwt { jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()) }
            }
            .httpBasic{ it.disable() }
            .build()
    }
}
