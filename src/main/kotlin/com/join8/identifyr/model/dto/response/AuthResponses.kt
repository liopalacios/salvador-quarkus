package com.join8.identifyr.model.dto.response

import java.util.*

/** Respuesta de POST /auth/register-password y POST /auth/login. */
data class AuthTokenResponse(val token: String, val userId: UUID) {
    override fun toString() = "AuthTokenResponse(userId=$userId)"
}

/** Respuesta de PUT /users/{userId}/dni-photos. */
data class DniPhotosResponse(val userId: UUID, val dniFrontPhotoId: String, val dniBackPhotoId: String)
