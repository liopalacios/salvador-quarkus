package com.join8.identifyr.model.dto.response

/** Contrato público de GET /users/phone/{phone}: solo indica si existe, sin datos del usuario. */
data class PhoneExistsResponse(val exists: Boolean)
