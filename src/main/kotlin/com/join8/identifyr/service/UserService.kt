package com.join8.identifyr.service

import com.join8.identifyr.model.dto.request.UserRegisterRequest
import com.join8.identifyr.model.dto.response.UserRegisterResponse
import com.join8.identifyr.model.entity.User
import com.join8.identifyr.repository.UserRepository
import jakarta.transaction.Transactional
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: BCryptPasswordEncoder
) {

    @Transactional
    fun registerUser(request: UserRegisterRequest): UserRegisterResponse {
        // Validar que DNI no esté registrado
        require(!userRepository.existsByDni(request.dniFrontPhotoId)) {
            "La id foto ${request.dniFrontPhotoId} ya está registrado"
        }
        require(!userRepository.existsByEmail(request.dniBackPhotoId)) {
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
    fun findByDni(dni: String): User? = userRepository.findByDni(dni)
    fun findByEmail(email: String): User? = userRepository.findByEmail(email)
    fun existsById(id: java.util.UUID): Boolean = userRepository.existsById(id)
    fun findById(id: java.util.UUID): User? = userRepository.findById(id).orElse(null)
}
