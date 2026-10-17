package com.join8.identifyr.model.dto.response

import java.util.*

data class UserRegisterResponse (
    val id: UUID,
    val dni: String,
    val fullName: String,
    val phone: String,
    val email: String,
    val address: String?,
    val nationality: String?,
    val isVerified: Boolean,
    val message: String? = null
)