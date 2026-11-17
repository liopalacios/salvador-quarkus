package com.join8.identifyr.exception

import java.time.Instant

/** Token válido sin permisos sobre el recurso. Se traduce a 403 en [GlobalExceptionHandler]. */
class ForbiddenException(message: String, val code: String = "FORBIDDEN") : RuntimeException(message)

/**
 * Celular inexistente o contraseña incorrecta: mismo 401 y mismo mensaje en ambos casos
 * (no revela si el celular existe).
 */
class InvalidCredentialsException : RuntimeException("Celular o contraseña incorrectos")

/** Login bloqueado por intentos fallidos. Se traduce a 423 con Retry-After. */
class AccountLockedException(val lockedUntil: Instant) :
    RuntimeException("Demasiados intentos fallidos. Intenta nuevamente más tarde")

/** Funcionalidad preparada pero aún no disponible (501). */
class NotImplementedException(message: String) : RuntimeException(message)
