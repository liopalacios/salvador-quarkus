package com.join8.identifyr.security

import com.join8.identifyr.model.entity.Role
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken
import java.util.*

/**
 * Usuario autenticado por el token, disponible en los controladores con `@AuthenticationPrincipal`.
 * [subject] es el claim "sub" (el userId en los tokens que emite esta API).
 */
data class AuthenticatedUser(val subject: String, val phone: String?, val role: Role) {
    val isSupervisor: Boolean get() = role == Role.SUPERVISOR

    /** userId del token; null si "sub" no es un UUID. */
    val userId: UUID? get() = runCatching { UUID.fromString(subject) }.getOrNull()

    fun isUser(id: UUID?): Boolean = id != null && userId == id
    fun isUser(id: String?): Boolean =
        id != null && runCatching { UUID.fromString(id.trim()) }.getOrNull()?.let { isUser(it) } == true
    fun ownsPhone(phone: String?): Boolean = phone != null && this.phone == phone

    override fun toString() = "AuthenticatedUser(subject=$subject, role=$role)"
}

/** Authentication cuyo principal es [AuthenticatedUser]; el token nunca aparece en toString. */
class AuthenticatedUserToken(
    jwt: Jwt,
    private val user: AuthenticatedUser,
    authorities: Collection<GrantedAuthority>
) : AbstractOAuth2TokenAuthenticationToken<Jwt>(jwt, user, jwt, authorities) {
    init { isAuthenticated = true }

    override fun getTokenAttributes(): Map<String, Any> = token.claims
    override fun getName(): String = user.subject
    override fun getPrincipal(): AuthenticatedUser = user
    override fun toString() = "AuthenticatedUserToken($user)"
}
