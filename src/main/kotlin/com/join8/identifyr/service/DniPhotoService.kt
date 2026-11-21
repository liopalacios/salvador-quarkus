package com.join8.identifyr.service

import com.join8.identifyr.exception.ResourceNotFoundException
import com.join8.identifyr.model.dto.response.DniPhotosResponse
import com.join8.identifyr.model.entity.DniPhoto
import com.join8.identifyr.model.entity.DniPhotoSide
import com.join8.identifyr.repository.DniPhotoRepository
import com.join8.identifyr.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime
import java.util.*

@Service
class DniPhotoService(
    private val userRepository: UserRepository,
    private val dniPhotoRepository: DniPhotoRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Guarda las dos imágenes del DNI (reemplaza las anteriores del usuario) y escribe sus ids
     * en users.photo_front / photo_back.
     *  - Archivo vacío, de más de [MAX_BYTES] o que no sea JPEG/PNG -> 400.
     *  - Usuario inexistente -> 404.
     */
    @Transactional
    fun upload(userId: UUID, front: MultipartFile, back: MultipartFile): DniPhotosResponse {
        val frontImage = readImage(front, "frontal")
        val backImage = readImage(back, "reverso")

        // FOR UPDATE: dos subidas simultáneas del mismo usuario no chocan con el índice único
        val user = userRepository.findByIdForUpdate(userId) ?: throw ResourceNotFoundException("El usuario no existe")

        dniPhotoRepository.deleteByUserId(userId)
        val savedFront = dniPhotoRepository.save(frontImage.toEntity(userId, DniPhotoSide.FRONT))
        val savedBack = dniPhotoRepository.save(backImage.toEntity(userId, DniPhotoSide.BACK))

        user.photoFront = savedFront.id.toString()
        user.photoBack = savedBack.id.toString()
        user.updatedAt = LocalDateTime.now()
        userRepository.save(user)

        log.info("Fotos del DNI del usuario {} guardadas ({} B y {} B)", userId, frontImage.bytes.size, backImage.bytes.size)
        return DniPhotosResponse(userId = userId, dniFrontPhotoId = user.photoFront!!, dniBackPhotoId = user.photoBack!!)
    }

    private class Image(val bytes: ByteArray, val contentType: String) {
        fun toEntity(userId: UUID, side: DniPhotoSide) =
            DniPhoto(userId = userId, side = side, contentType = contentType, sizeBytes = bytes.size, content = bytes)
    }

    // El tipo se decide por los primeros bytes del archivo, no por el Content-Type que manda el cliente
    private fun readImage(file: MultipartFile, label: String): Image {
        require(!file.isEmpty) { "La foto $label está vacía" }
        require(file.size <= MAX_BYTES) { "La foto $label supera los 5 MB" }
        val bytes = file.bytes
        val contentType = when {
            bytes.startsWith(JPEG_MAGIC) -> "image/jpeg"
            bytes.startsWith(PNG_MAGIC) -> "image/png"
            else -> throw IllegalArgumentException("La foto $label debe ser JPEG o PNG")
        }
        return Image(bytes, contentType)
    }

    private fun ByteArray.startsWith(prefix: ByteArray) =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    companion object {
        const val MAX_BYTES = 5L * 1024 * 1024
        private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }
}
