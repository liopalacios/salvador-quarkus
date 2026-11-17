package com.join8.identifyr.model.dto.response

import com.fasterxml.jackson.annotation.JsonInclude
import java.util.*

/**
 * Respuesta de POST /users/register y GET /users/{id}.
 * Contrato con la app: los null se envían explícitos ("email": null), no se omiten.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
data class UserRegisterResponse (
    val id: UUID,
    val phone: String,
    val email: String? = null,
    val address: String? = null,
    val nationality: String? = null,
    val isVerified: Boolean,
    val message: String? = null
)
