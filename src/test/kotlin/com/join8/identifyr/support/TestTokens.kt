package com.join8.identifyr.support

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import javax.crypto.spec.SecretKeySpec

/** Tokens HS256 con el mismo formato que emite JwtService, para armar casos en los tests. */
object TestTokens {

    fun create(
        secret: String,
        subject: Any,
        phone: String? = null,
        role: String? = "USER",
        expiresAt: Instant = Instant.now().plus(1, ChronoUnit.HOURS)
    ): String {
        val encoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(SecretKeySpec(secret.toByteArray(), "HmacSHA256")))
        val claims = JwtClaimsSet.builder()
            .subject(subject.toString())
            .issuedAt(expiresAt.minus(2, ChronoUnit.HOURS))
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())
            .apply {
                phone?.let { claim("phone", it) }
                role?.let { claim("role", it) }
            }
            .build()
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
    }

    fun user(secret: String, userId: UUID, phone: String) = create(secret, userId, phone, "USER")

    fun supervisor(secret: String, subject: Any = UUID.randomUUID()) = create(secret, subject, "900000000", "SUPERVISOR")
}
