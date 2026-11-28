package com.join8.identifyr.model.dto.request

import jakarta.validation.constraints.NotBlank

/** Body de POST /users/{userId}/notifications/read: la notificación (type + referenceId) que el cliente ya vio. */
data class NotificationReadRequest(
    @field:NotBlank(message = "es obligatorio")
    val type: String? = null,
    val referenceId: String? = null
)
