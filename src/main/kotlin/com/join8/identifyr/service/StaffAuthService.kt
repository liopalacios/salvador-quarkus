package com.join8.identifyr.service

import com.join8.identifyr.config.AuthProperties
import com.join8.identifyr.exception.AccountLockedException
import com.join8.identifyr.exception.InvalidCredentialsException
import com.join8.identifyr.model.dto.request.StaffLoginRequest
import com.join8.identifyr.model.dto.response.AuthTokenResponse
import com.join8.identifyr.model.entity.LoginAttempt
import com.join8.identifyr.repository.LoginAttemptRepository
import com.join8.identifyr.repository.StaffUserRepository
import com.join8.identifyr.security.JwtService
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

/**
 * Login del personal interno (supervisor) con usuario + contraseña fuerte.
 * Mismas reglas que el login de clientes ([AuthService.login]):
 *  - Usuario inexistente, deshabilitado o contraseña errónea -> 401 con el mismo mensaje.
 *  - [AuthProperties.maxFailedAttempts] fallos consecutivos bloquean la cuenta [AuthProperties.lockDuration] (423).
 *  - Login exitoso -> contador a 0 y token con role del usuario (SUPERVISOR).
 */
@Service
class StaffAuthService(
    private val staffUserRepository: StaffUserRepository,
    private val loginAttemptRepository: LoginAttemptRepository,
    private val passwordEncoder: BCryptPasswordEncoder,
    private val jwtService: JwtService,
    private val props: AuthProperties,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Con usuario inexistente igual se ejecuta un BCrypt: el tiempo no revela si el usuario existe
    private val dummyHash: String by lazy { passwordEncoder.encode(UUID.randomUUID().toString()) }

    // noRollbackFor: el contador, el bloqueo y la auditoría se guardan aunque se responda error
    @Transactional(noRollbackFor = [InvalidCredentialsException::class, AccountLockedException::class])
    fun login(request: StaffLoginRequest, ip: String?): AuthTokenResponse {
        val username = request.username!!.trim()
        val password = request.password!!
        val now = clock.instant()

        val staff = staffUserRepository.findByUsernameForUpdate(username)
        if (staff == null || !staff.enabled) {
            passwordEncoder.matches(password, dummyHash)
            audit(staff?.id, username, ip, success = false, reason = if (staff == null) "UNKNOWN_USERNAME" else "DISABLED")
            throw InvalidCredentialsException(INVALID_MESSAGE)
        }

        staff.lockedUntil?.let { until ->
            if (until.isAfter(now)) {
                audit(staff.id, username, ip, success = false, reason = "LOCKED")
                throw AccountLockedException(until)
            }
            // Bloqueo vencido: empieza de cero
            staff.lockedUntil = null
            staff.failedLoginAttempts = 0
        }

        if (!passwordEncoder.matches(password, staff.passwordHash)) {
            staff.failedLoginAttempts += 1
            staff.updatedAt = localNow()
            if (staff.failedLoginAttempts >= props.maxFailedAttempts) {
                val until = now.plus(props.lockDuration)
                staff.lockedUntil = until
                staffUserRepository.save(staff)
                audit(staff.id, username, ip, success = false, reason = "LOCKED_NOW")
                log.warn("Login de personal bloqueado para {} hasta {}", staff.id, until)
                throw AccountLockedException(until)
            }
            staffUserRepository.save(staff)
            audit(staff.id, username, ip, success = false, reason = "BAD_PASSWORD")
            throw InvalidCredentialsException(INVALID_MESSAGE)
        }

        staff.failedLoginAttempts = 0
        staff.lockedUntil = null
        staff.updatedAt = localNow()
        staffUserRepository.save(staff)

        audit(staff.id, username, ip, success = true, reason = null)
        return AuthTokenResponse(token = jwtService.issue(staff), userId = staff.id!!)
    }

    /** Auditoría: fecha, usuario, IP y resultado. Nunca la contraseña. */
    private fun audit(staffId: UUID?, username: String, ip: String?, success: Boolean, reason: String?) {
        loginAttemptRepository.save(
            LoginAttempt(
                staffUserId = staffId, username = username.take(50), phone = null, ipAddress = ip?.take(45),
                attemptType = LoginAttempt.TYPE_STAFF_LOGIN, isSuccessful = success, failureReason = reason,
                createdAt = localNow()
            )
        )
        log.info("Auth {} staff={} ip={} resultado={}", LoginAttempt.TYPE_STAFF_LOGIN, staffId ?: "-", ip, reason ?: "OK")
    }

    // application_attempts.created_at es TIMESTAMP sin zona escrito con la zona del sistema
    private fun localNow(): LocalDateTime = LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault())

    private companion object {
        const val INVALID_MESSAGE = "Usuario o contraseña incorrectos"
    }
}
