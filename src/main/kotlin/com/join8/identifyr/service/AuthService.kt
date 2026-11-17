package com.join8.identifyr.service

import com.join8.identifyr.config.AuthProperties
import com.join8.identifyr.exception.*
import com.join8.identifyr.model.dto.request.LoginRequest
import com.join8.identifyr.model.dto.request.RegisterPasswordRequest
import com.join8.identifyr.model.dto.response.AuthTokenResponse
import com.join8.identifyr.model.entity.LoginAttempt
import com.join8.identifyr.model.entity.Role
import com.join8.identifyr.repository.LoginAttemptRepository
import com.join8.identifyr.repository.UserRepository
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
 * Contraseña de 4 dígitos (BCrypt) + emisión de JWT. La contraseña nunca se registra ni se guarda en claro.
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val loginAttemptRepository: LoginAttemptRepository,
    private val passwordEncoder: BCryptPasswordEncoder,
    private val jwtService: JwtService,
    private val props: AuthProperties,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Hash de relleno: con celular inexistente o sin contraseña igual se ejecuta un BCrypt,
    // así el tiempo de respuesta no revela si el celular está registrado.
    private val dummyHash: String by lazy { passwordEncoder.encode(UUID.randomUUID().toString()) }

    /**
     * Crea la contraseña y devuelve el token. Solo si el usuario existe, aún no tiene contraseña (409)
     * y está dentro de la ventana posterior al registro (403): un userId filtrado no sirve para tomar la cuenta.
     */
    @Transactional
    fun registerPassword(request: RegisterPasswordRequest, ip: String?): AuthTokenResponse {
        val userId = UUID.fromString(request.userId!!.trim())
        // FOR UPDATE: dos registros simultáneos de contraseña no pueden pisarse
        val user = userRepository.findByIdForUpdate(userId)
            ?: throw ResourceNotFoundException("El usuario no existe")

        if (user.hasPassword) throw ConflictException("El usuario ya tiene una contraseña registrada")

        val deadline = user.createdAt.plus(props.passwordRegistrationWindow)
        if (localNow().isAfter(deadline)) {
            throw ForbiddenException(
                "El plazo para crear la contraseña venció; vuelve a validar tu celular",
                code = "PASSWORD_REGISTRATION_EXPIRED"
            )
        }

        user.passwordHash = passwordEncoder.encode(request.password!!)
        user.failedLoginAttempts = 0
        user.lockedUntil = null
        user.updatedAt = localNow()
        userRepository.save(user)

        audit(user.id, user.phone, ip, success = true, reason = null, type = LoginAttempt.TYPE_REGISTRATION)
        log.info("Contraseña registrada para el usuario {}", user.id)
        return AuthTokenResponse(token = jwtService.issue(user), userId = user.id!!)
    }

    /**
     * Login por celular + contraseña.
     *  - Celular inexistente o contraseña errónea -> 401 con el mismo mensaje.
     *  - Al llegar a [AuthProperties.maxFailedAttempts] fallos consecutivos el celular queda bloqueado
     *    [AuthProperties.lockDuration]: ese intento y los siguientes responden 423 (aun con la contraseña correcta).
     *  - Login exitoso -> contador a 0.
     * [requiredRole] = SUPERVISOR para el login de supervisor (un USER con la contraseña correcta recibe 401).
     *
     * noRollbackFor: el contador, el bloqueo y la auditoría deben guardarse aunque se responda error.
     */
    @Transactional(noRollbackFor = [InvalidCredentialsException::class, AccountLockedException::class])
    fun login(request: LoginRequest, ip: String?, requiredRole: Role? = null): AuthTokenResponse {
        val phone = request.phone!!
        val password = request.password!!
        val now = clock.instant()

        val user = userRepository.findAllByPhoneForUpdate(phone).firstOrNull()
        if (user == null) {
            passwordEncoder.matches(password, dummyHash)
            audit(null, phone, ip, success = false, reason = "UNKNOWN_PHONE")
            throw InvalidCredentialsException()
        }

        user.lockedUntil?.let { until ->
            if (until.isAfter(now)) {
                audit(user.id, phone, ip, success = false, reason = "LOCKED")
                throw AccountLockedException(until)
            }
            // Bloqueo vencido: empieza de cero
            user.lockedUntil = null
            user.failedLoginAttempts = 0
        }

        val matches = if (user.hasPassword) passwordEncoder.matches(password, user.passwordHash)
        else passwordEncoder.matches(password, dummyHash).let { false }

        if (!matches) {
            user.failedLoginAttempts += 1
            if (user.failedLoginAttempts >= props.maxFailedAttempts) {
                val until = now.plus(props.lockDuration)
                user.lockedUntil = until
                userRepository.save(user)
                audit(user.id, phone, ip, success = false, reason = "LOCKED_NOW")
                log.warn("Login bloqueado para el celular {} hasta {}", mask(phone), until)
                throw AccountLockedException(until)
            }
            userRepository.save(user)
            audit(user.id, phone, ip, success = false, reason = if (user.hasPassword) "BAD_PASSWORD" else "NO_PASSWORD")
            throw InvalidCredentialsException()
        }

        user.failedLoginAttempts = 0
        user.lockedUntil = null
        userRepository.save(user)

        if (requiredRole != null && user.role != requiredRole) {
            audit(user.id, phone, ip, success = false, reason = "NOT_${requiredRole.name}")
            throw InvalidCredentialsException()
        }

        audit(user.id, phone, ip, success = true, reason = null)
        return AuthTokenResponse(token = jwtService.issue(user), userId = user.id!!)
    }

    /** Auditoría: fecha, celular, IP y resultado. Nunca la contraseña. */
    private fun audit(
        userId: UUID?, phone: String, ip: String?, success: Boolean, reason: String?,
        type: String = LoginAttempt.TYPE_LOGIN
    ) {
        loginAttemptRepository.save(
            LoginAttempt(
                userId = userId, phone = phone, ipAddress = ip?.take(45), attemptType = type,
                isSuccessful = success, failureReason = reason, createdAt = localNow()
            )
        )
        log.info("Auth {} celular={} ip={} resultado={}", type, mask(phone), ip, reason ?: "OK")
    }

    // users.created_at / application_attempts.created_at son TIMESTAMP sin zona escritos con la zona del sistema
    private fun localNow(): LocalDateTime = LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault())

    private fun mask(phone: String) = "${phone.take(3)}***${phone.takeLast(3)}"
}
