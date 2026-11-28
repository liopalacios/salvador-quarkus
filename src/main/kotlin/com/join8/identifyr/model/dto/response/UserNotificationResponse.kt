package com.join8.identifyr.model.dto.response

/**
 * Notificación pendiente del usuario (GET /users/{userId}/notifications).
 * [type] es estable y la app lo usa para decidir a dónde llevar al usuario; [title] y [message] se muestran tal cual.
 */
data class UserNotificationResponse(
    val type: String,
    val title: String,
    val message: String,
    /** Recurso al que se refiere (p. ej. idSolicitud en LOAN_REQUEST_REJECTED); se usa para marcarla como leída. */
    val referenceId: String? = null
) {
    companion object {
        const val TYPE_DNI_PHOTOS_PENDING = "DNI_PHOTOS_PENDING"
        const val TYPE_LOAN_REQUEST_REJECTED = "LOAN_REQUEST_REJECTED"
        const val TYPE_PAYMENT_REJECTED = "PAYMENT_REJECTED"
    }
}
