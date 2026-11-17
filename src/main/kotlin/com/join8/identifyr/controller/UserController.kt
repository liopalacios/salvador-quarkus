package com.join8.identifyr.controller

import com.join8.identifyr.model.dto.request.DniPhotosRequest
import com.join8.identifyr.model.dto.request.UserRegisterRequest
import com.join8.identifyr.model.dto.response.DniPhotosResponse
import com.join8.identifyr.model.dto.response.PhoneExistsResponse
import com.join8.identifyr.model.dto.response.UserRegisterResponse
import com.join8.identifyr.security.AccessGuard
import com.join8.identifyr.security.AuthenticatedUser
import com.join8.identifyr.service.UserService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/users")
class UserController(private val userService: UserService) {

    // Público. 201 · 400 formato · 409 celular ya registrado
    @PostMapping("/register")
    fun registerUser(
        @Valid @RequestBody request: UserRegisterRequest
    ): ResponseEntity<UserRegisterResponse> {
        val response = userService.registerUser(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    // Público: lo llama la app antes del login. La app solo evalúa el código: 200 -> login, 404 -> sigue el registro.
    @GetMapping("/phone/{phone}")
    fun phoneExists(@PathVariable phone: String): ResponseEntity<PhoneExistsResponse> {
        val exists = userService.phoneExists(phone)
        return ResponseEntity.status(if (exists) HttpStatus.OK else HttpStatus.NOT_FOUND).body(PhoneExistsResponse(exists))
    }

    // Protegido: el propio usuario o un supervisor
    @GetMapping("/{id}")
    fun getUser(
        @PathVariable id: UUID,
        @AuthenticationPrincipal auth: AuthenticatedUser
    ): ResponseEntity<UserRegisterResponse> {
        AccessGuard.requireOwnerOrSupervisor(auth, id)
        val user = userService.findById(id)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(
            UserRegisterResponse(
                id = user.id!!,
                phone = user.phone,
                email = user.email.ifBlank { null },
                address = user.address,
                nationality = user.nationality,
                isVerified = user.isVerified
            )
        )
    }

    // Protegido: solo el propio usuario. 200 · 400 · 403 · 404 · 409 foto usada por otro usuario
    @PutMapping("/{userId}/dni-photos")
    fun updateDniPhotos(
        @PathVariable userId: UUID,
        @Valid @RequestBody request: DniPhotosRequest,
        @AuthenticationPrincipal auth: AuthenticatedUser
    ): ResponseEntity<DniPhotosResponse> {
        AccessGuard.requireOwner(auth, userId)
        return ResponseEntity.ok(userService.updateDniPhotos(userId, request))
    }
}
