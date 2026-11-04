package com.join8.identifyr.service

import com.join8.identifyr.model.dto.request.UserRegisterRequest
import com.join8.identifyr.model.dto.response.UserRegisterResponse
import com.join8.identifyr.model.entity.User
import com.join8.identifyr.repository.UserRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: BCryptPasswordEncoder
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun registerUser(request: UserRegisterRequest): UserRegisterResponse {
        // Validar que las fotos del documento no estén registradas
        require(!userRepository.existsByPhotoFront(request.dniFrontPhotoId)) {
            "La id foto ${request.dniFrontPhotoId} ya está registrado"
        }
        require(!userRepository.existsByPhotoBack(request.dniBackPhotoId)) {
            "La id foto reversa ${request.dniBackPhotoId} ya está registrado"
        }
        require(!userRepository.existsByPhone(request.phone)) {
            "El celular ${request.phone} ya está registrado"
        }

        // Crear el usuario
        val user = User(
            phone = request.phone,
            email = "",
            photoFront = request.dniFrontPhotoId,
            photoBack = request.dniBackPhotoId,
            address = null,
            nationality = null,
            passwordHash = "",
            isVerified = false,
            verificationToken = null,
            tokenExpiresAt = LocalDateTime.now().plusDays(1),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val savedUser = userRepository.save(user)

        return UserRegisterResponse(
            id = savedUser.id!!,
            phone = savedUser.phone,
            email = savedUser.email,
            address = savedUser.address,
            nationality = savedUser.nationality,
            isVerified = savedUser.isVerified,
            message = "Usuario registrado exitosamente."
        )
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
    fun existsById(id: java.util.UUID): Boolean = userRepository.existsById(id)
    fun findById(id: java.util.UUID): User? = userRepository.findById(id).orElse(null)

    private companion object {
        val PHONE_FORMAT = Regex("[0-9]{9}")
    }
}
