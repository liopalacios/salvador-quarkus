package com.join8.identifyr.security

import com.join8.identifyr.model.entity.User
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

/** Emite los tokens HS256 de la API: sub=userId, phone, role, iat, exp, jti. */
@Service
class JwtService(
    private val encoder: JwtEncoder,
    @Value("\${jwt.expiration}") expirationMs: Long
) {
    private val ttl: Duration = Duration.ofMillis(expirationMs)

    fun issue(user: User): String {
        // Hora real (no el Clock inyectable): el decoder valida exp contra la hora del sistema
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val claims = JwtClaimsSet.builder()
            .subject(user.id!!.toString())
            .claim(CLAIM_PHONE, user.phone)
            .claim(CLAIM_ROLE, user.role.name)
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .id(UUID.randomUUID().toString())
            .build()
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
    }

    companion object {
        const val CLAIM_PHONE = "phone"
        const val CLAIM_ROLE = "role"
    }
}
