package com.join8.identifyr.model.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

const val PHONE_REGEX = "^\\d{9}$"
const val PASSWORD_REGEX = "^\\d{4}$"
const val UUID_REGEX = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"

// Los mensajes de validación nunca incluyen el valor recibido (la contraseña no debe volver en la respuesta)

/** POST /auth/register-password */
data class RegisterPasswordRequest(
    @field:NotBlank(message = "es obligatorio")
    @field:Pattern(regexp = UUID_REGEX, message = "debe ser un UUID válido")
    val userId: String? = null,

    @field:NotBlank(message = "es obligatoria")
    @field:Pattern(regexp = PASSWORD_REGEX, message = "debe tener exactamente 4 dígitos")
    val password: String? = null
) {
    override fun toString() = "RegisterPasswordRequest(userId=$userId)"
}

/** POST /auth/login y POST /auth/supervisor/login */
data class LoginRequest(
    @field:NotBlank(message = "es obligatorio")
    @field:Pattern(regexp = PHONE_REGEX, message = "debe tener 9 dígitos")
    val phone: String? = null,

    @field:NotBlank(message = "es obligatoria")
    @field:Pattern(regexp = PASSWORD_REGEX, message = "debe tener exactamente 4 dígitos")
    val password: String? = null
) {
    override fun toString() = "LoginRequest(phone=***)"
}

/** POST /auth/staff/login: usuario + contraseña fuerte del personal interno (supervisor). */
data class StaffLoginRequest(
    @field:NotBlank(message = "es obligatorio")
    @field:Size(max = 50, message = "no puede tener más de 50 caracteres")
    val username: String? = null,

    // BCrypt solo usa los primeros 72 bytes
    @field:NotBlank(message = "es obligatoria")
    @field:Size(max = 72, message = "no puede tener más de 72 caracteres")
    val password: String? = null
) {
    override fun toString() = "StaffLoginRequest(username=***)"
}

/** POST /auth/otp/send (siguiente fase) */
data class OtpSendRequest(
    @field:NotBlank(message = "es obligatorio")
    @field:Pattern(regexp = PHONE_REGEX, message = "debe tener 9 dígitos")
    val phone: String? = null
)

/** POST /auth/otp/verify (siguiente fase) */
data class OtpVerifyRequest(
    @field:NotBlank(message = "es obligatorio")
    @field:Pattern(regexp = PHONE_REGEX, message = "debe tener 9 dígitos")
    val phone: String? = null,

    @field:NotBlank(message = "es obligatorio")
    @field:Pattern(regexp = "^\\d{4,8}$", message = "debe tener entre 4 y 8 dígitos")
    val code: String? = null
) {
    override fun toString() = "OtpVerifyRequest(phone=***)"
}

/** PUT /users/{userId}/dni-photos */
data class DniPhotosRequest(
    @field:NotBlank(message = "es obligatorio")
    @field:jakarta.validation.constraints.Size(max = 255, message = "no puede tener más de 255 caracteres")
    val dniFrontPhotoId: String? = null,

    @field:NotBlank(message = "es obligatorio")
    @field:jakarta.validation.constraints.Size(max = 255, message = "no puede tener más de 255 caracteres")
    val dniBackPhotoId: String? = null
)
