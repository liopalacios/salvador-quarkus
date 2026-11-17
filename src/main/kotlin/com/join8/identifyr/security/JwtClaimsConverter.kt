package com.join8.identifyr.security

import com.join8.identifyr.model.entity.Role
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Convierte el JWT ya validado (firma, exp, sub) en [AuthenticatedUserToken]: así el usuario
 * (userId, phone, role) queda en el contexto de seguridad de la petición.
 * Rol: claim "role" ("USER" | "SUPERVISOR"); se acepta también "roles" (lista) de tokens anteriores.
 * Un rol ausente o desconocido se trata como USER (mínimo privilegio).
 */
class JwtClaimsConverter : Converter<Jwt, AbstractAuthenticationToken> {

    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val role = roleOf(jwt)
        val user = AuthenticatedUser(subject = jwt.subject, phone = jwt.getClaimAsString(JwtService.CLAIM_PHONE), role = role)
        return AuthenticatedUserToken(jwt, user, listOf(SimpleGrantedAuthority("ROLE_${role.name}")))
    }

    private fun roleOf(jwt: Jwt): Role {
        val names = buildList {
            jwt.getClaimAsString(JwtService.CLAIM_ROLE)?.let { add(it) }
            runCatching { jwt.getClaimAsStringList("roles") }.getOrNull()?.let { addAll(it) }
        }
        return if (names.any { it.equals(Role.SUPERVISOR.name, ignoreCase = true) }) Role.SUPERVISOR else Role.USER
    }
}
