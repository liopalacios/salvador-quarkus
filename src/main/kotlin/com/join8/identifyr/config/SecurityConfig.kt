package com.join8.identifyr.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.join8.identifyr.security.*
import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import jakarta.servlet.DispatcherType
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.*
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.channel.ChannelProcessingFilter
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Autenticación JWT (Bearer) para TODOS los métodos HTTP, salvo la lista blanca pública de [PUBLIC_ENDPOINTS].
 * Token ausente/inválido/vencido -> 401 { "error": "UNAUTHORIZED" }; sin el rol requerido -> 403.
 * La autorización por recurso (dueño del recurso) se hace en los controladores con [AccessGuard].
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    companion object {
        const val ROLE_SUPERVISOR = "SUPERVISOR"
        const val MIN_SECRET_BYTES = 32 // HS256: clave de al menos 256 bits

        /** Rutas públicas (relativas al context-path /api/v1). */
        val PUBLIC_ENDPOINTS: List<Pair<HttpMethod, String>> = listOf(
            HttpMethod.GET to "/users/phone/*",
            HttpMethod.POST to "/users/register",
            HttpMethod.POST to "/auth/register-password",
            HttpMethod.POST to "/auth/login",
            HttpMethod.POST to "/auth/supervisor/login",
            HttpMethod.POST to "/auth/staff/login",
            HttpMethod.POST to "/auth/otp/send",
            HttpMethod.POST to "/auth/otp/verify",
            HttpMethod.GET to "/actuator/health",
            HttpMethod.GET to "/actuator/health/**",
            HttpMethod.GET to "/api-docs/**",
            HttpMethod.GET to "/swagger-ui/**",
            HttpMethod.GET to "/swagger-ui.html"
        )
    }

    // Costo explícito (≥ 10). BCrypt compara en tiempo constante.
    @Bean
    fun passwordEncoder(): BCryptPasswordEncoder = BCryptPasswordEncoder(10)

    /** Clave HS256: bytes UTF-8 de jwt.secret (JWT_SECRET, desde Key Vault). Falla el arranque si es corta. */
    @Bean
    fun jwtSecretKey(@Value("\${jwt.secret}") secret: String): SecretKey {
        val bytes = secret.toByteArray(Charsets.UTF_8)
        require(bytes.size >= MIN_SECRET_BYTES) { "jwt.secret debe tener al menos $MIN_SECRET_BYTES bytes (256 bits)" }
        return SecretKeySpec(bytes, "HmacSHA256")
    }

    /** Valida firma HS256, exp/nbf (60 s de tolerancia) y que exista "sub". */
    @Bean
    fun jwtDecoder(jwtSecretKey: SecretKey): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build().apply {
            setJwtValidator(
                DelegatingOAuth2TokenValidator(
                    JwtValidators.createDefault(),
                    JwtClaimValidator<Any?>(JwtClaimNames.SUB) { it is String && it.isNotBlank() }
                )
            )
        }

    @Bean
    fun jwtEncoder(jwtSecretKey: SecretKey): JwtEncoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(jwtSecretKey))

    /**
     * - En las rutas públicas el header Authorization se ignora: un token viejo o vencido que la app
     *   todavía envíe no debe bloquear el login ni la consulta de celular.
     * - Header sin token ("Bearer", "Bearer   ", "Bearer null") = sin token: en las rutas protegidas
     *   termina en 401 igual que sin header.
     */
    private fun bearerTokenResolver(): BearerTokenResolver {
        val delegate = DefaultBearerTokenResolver()
        val publicMatchers = PUBLIC_ENDPOINTS.map { (method, path) -> AntPathRequestMatcher(path, method.name()) }
        return BearerTokenResolver { request ->
            if (publicMatchers.any { it.matches(request) }) return@BearerTokenResolver null
            val header = request.getHeader(HttpHeaders.AUTHORIZATION)?.trim()
            val token = header?.takeIf { it.startsWith("bearer", ignoreCase = true) }?.substring(6)?.trim()
            if (token != null && (token.isEmpty() || token.equals("null", ignoreCase = true))) null
            else delegate.resolve(request)
        }
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        objectMapper: ObjectMapper,
        @Value("\${security.require-https:false}") requireHttps: Boolean
    ): SecurityFilterChain {
        val errorWriter = SecurityErrorWriter(objectMapper)
        val entryPoint = JsonAuthenticationEntryPoint(errorWriter)
        val accessDenied = JsonAccessDeniedHandler(errorWriter)

        if (requireHttps) http.addFilterBefore(HttpsEnforcementFilter(errorWriter), ChannelProcessingFilter::class.java)

        return http
            .csrf { it.disable() }
            .cors { }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                // El reenvío interno a /error no debe convertir un 4xx/5xx en 401
                auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                PUBLIC_ENDPOINTS.forEach { (method, path) -> auth.requestMatchers(method, path).permitAll() }
                auth
                    // Solo supervisor
                    .requestMatchers(HttpMethod.GET, "/loan-requests", "/loan-requests/**").hasRole(ROLE_SUPERVISOR)
                    .requestMatchers(HttpMethod.POST, "/loans/register").hasRole(ROLE_SUPERVISOR)
                    .requestMatchers(HttpMethod.POST, "/loan-requests/*/reject").hasRole(ROLE_SUPERVISOR)
                    .requestMatchers(HttpMethod.GET, "/loan-payments", "/loan-payments/**").hasRole(ROLE_SUPERVISOR)
                    .requestMatchers(HttpMethod.POST, "/loan-payments/**").hasRole(ROLE_SUPERVISOR)
                    // Todo lo demás (GET, POST, PUT, DELETE, ...): token válido; el dueño se valida en el controlador
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer {
                it.bearerTokenResolver(bearerTokenResolver())
                it.authenticationEntryPoint(entryPoint)
                it.accessDeniedHandler(accessDenied)
                it.jwt { jwt -> jwt.jwtAuthenticationConverter(JwtClaimsConverter()) }
            }
            .exceptionHandling {
                it.authenticationEntryPoint(entryPoint)
                it.accessDeniedHandler(accessDenied)
            }
            .build()
    }
}
