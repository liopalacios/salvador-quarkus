package com.join8.identifyr.model.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

enum class DniPhotoSide { FRONT, BACK }

/** Imagen del DNI (JPEG o PNG). Su id es el que se guarda en users.photo_front / photo_back. */
@Entity
@Table(name = "dni_photos")
class DniPhoto(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    val id: UUID? = null,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "side", length = 10, nullable = false)
    val side: DniPhotoSide,

    @Column(name = "content_type", length = 20, nullable = false)
    val contentType: String,

    @Column(name = "size_bytes", nullable = false)
    val sizeBytes: Int,

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "content", nullable = false)
    val content: ByteArray,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    // Sin el contenido: un log no debe volcar la imagen
    override fun toString() = "DniPhoto(id=$id, userId=$userId, side=$side, sizeBytes=$sizeBytes)"
}
