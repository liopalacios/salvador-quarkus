package com.join8.identifyr.service

import com.join8.identifyr.exception.ConflictException
import com.join8.identifyr.exception.ResourceNotFoundException
import com.join8.identifyr.model.dto.request.DniPhotosRequest
import com.join8.identifyr.model.dto.request.UserRegisterRequest
import com.join8.identifyr.model.dto.response.DniPhotosResponse
import com.join8.identifyr.model.dto.response.UserRegisterResponse
import com.join8.identifyr.model.entity.User
import com.join8.identifyr.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class UserService(
    private val userRepository: UserRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Crea el usuario sin contraseña ni fotos. Celular ya registrado -> 409. */
    @Transactional
    fun registerUser(request: UserRegisterRequest): UserRegisterResponse {
        val phone = request.phone!!
        if (userRepository.existsByPhone(phone)) throw ConflictException("El celular ya está registrado")

        val front = request.dniFrontPhotoId?.trim()?.takeIf { it.isNotEmpty() }
        val back = request.dniBackPhotoId?.trim()?.takeIf { it.isNotEmpty() }
        front?.let { require(!userRepository.existsByPhotoFront(it)) { "La foto frontal del documento ya está registrada" } }
        back?.let { require(!userRepository.existsByPhotoBack(it)) { "La foto reversa del documento ya está registrada" } }

        val now = LocalDateTime.now()
        val savedUser = try {
            userRepository.saveAndFlush(
                User(
                    phone = phone,
                    email = "",
                    photoFront = front,
                    photoBack = back,
                    passwordHash = null,
                    isVerified = false,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } catch (e: DataIntegrityViolationException) {
            // Carrera con otro registro del mismo celular (índice único uq_users_phone)
            throw ConflictException("El celular ya está registrado")
        }
        log.info("Usuario {} registrado", savedUser.id)

        return UserRegisterResponse(
            id = savedUser.id!!,
            phone = savedUser.phone,
            email = savedUser.email.ifBlank { null },
            isVerified = savedUser.isVerified,
            message = null
        )
    }

    /** Guarda (o reemplaza) las fotos del DNI. Una foto ya usada por otro usuario -> 409. */
    @Transactional
    fun updateDniPhotos(userId: UUID, request: DniPhotosRequest): DniPhotosResponse {
        val front = request.dniFrontPhotoId!!.trim()
        val back = request.dniBackPhotoId!!.trim()
        val user = userRepository.findByIdForUpdate(userId) ?: throw ResourceNotFoundException("El usuario no existe")

        if (userRepository.existsByPhotoFrontAndIdNot(front, userId) || userRepository.existsByPhotoBackAndIdNot(back, userId)) {
            throw ConflictException("La foto del documento ya está registrada en otro usuario")
        }
        user.photoFront = front
        user.photoBack = back
        user.updatedAt = LocalDateTime.now()
        userRepository.save(user)
        return DniPhotosResponse(userId = userId, dniFrontPhotoId = front, dniBackPhotoId = back)
    }

    /** Busca el mismo valor que guarda [registerUser]: los 9 dígitos, sin prefijo ni normalización. */
    fun phoneExists(phone: String): Boolean {
        // La validación va aquí y no con @Pattern: GlobalExceptionHandler traduce IllegalArgumentException a 400
        require(PHONE_FORMAT.matches(phone)) { "Celular debe tener 9 dígitos numéricos" }

        val exists = userRepository.existsByPhone(phone)
        log.info("Consulta de celular {}: existe={}", maskPhone(phone), exists)
        return exists
    }

    private fun maskPhone(phone: String) = "${phone.take(3)}***${phone.takeLast(3)}"

    fun findByEmail(email: String): User? = userRepository.findByEmail(email)
    fun existsById(id: UUID): Boolean = userRepository.existsById(id)
    fun findById(id: UUID): User? = userRepository.findById(id).orElse(null)

    private companion object {
        val PHONE_FORMAT = Regex("[0-9]{9}")
    }
}
