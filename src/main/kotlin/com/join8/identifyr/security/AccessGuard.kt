package com.join8.identifyr.security

import com.join8.identifyr.exception.ForbiddenException
import java.util.*

/**
 * Autorización por recurso: el usuario solo accede a lo suyo. Lanza [ForbiddenException] (403).
 * Las escrituras del cliente (requireOwner) no admiten supervisor; las lecturas (…OrSupervisor) sí.
 */
object AccessGuard {
    private const val MESSAGE = "No tienes permisos sobre este recurso"

    fun requireOwner(user: AuthenticatedUser, userId: UUID?) {
        if (!user.isUser(userId)) throw ForbiddenException(MESSAGE)
    }

    fun requireOwner(user: AuthenticatedUser, userId: String?) {
        if (!user.isUser(userId)) throw ForbiddenException(MESSAGE)
    }

    fun requirePhoneOwner(user: AuthenticatedUser, phone: String?) {
        if (!user.ownsPhone(phone)) throw ForbiddenException(MESSAGE)
    }

    fun requireOwnerOrSupervisor(user: AuthenticatedUser, userId: UUID?) {
        if (!user.isSupervisor && !user.isUser(userId)) throw ForbiddenException(MESSAGE)
    }

    fun requirePhoneOwnerOrSupervisor(user: AuthenticatedUser, phone: String?) {
        if (!user.isSupervisor && !user.ownsPhone(phone)) throw ForbiddenException(MESSAGE)
    }
}
