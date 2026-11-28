package com.join8.identifyr.controller

import com.join8.identifyr.model.dto.request.DniPhotosRequest
import com.join8.identifyr.model.dto.request.NotificationReadRequest
import com.join8.identifyr.model.dto.request.UserRegisterRequest
import com.join8.identifyr.model.dto.response.DniPhotosResponse
import com.join8.identifyr.model.dto.response.PhoneExistsResponse
import com.join8.identifyr.model.dto.response.UserNotificationResponse
import com.join8.identifyr.model.dto.response.UserRegisterResponse
import com.join8.identifyr.security.AccessGuard
import com.join8.identifyr.security.AuthenticatedUser
import com.join8.identifyr.service.DniPhotoService
import com.join8.identifyr.service.UserService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.*

@RestController
@RequestMapping("/users")
class UserController(
    private val userService: UserService,
    private val dniPhotoService: DniPhotoService
) {

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

    // Protegido: solo el propio usuario. 200 con la lista (vacía si no hay nada pendiente) · 403 · 404
    @GetMapping("/{userId}/notifications")
    fun notifications(
        @PathVariable userId: UUID,
        @AuthenticationPrincipal auth: AuthenticatedUser
    ): ResponseEntity<List<UserNotificationResponse>> {
        AccessGuard.requireOwner(auth, userId)
        return ResponseEntity.ok(userService.notifications(userId))
    }

    // Protegido: solo el propio usuario. Body {type, referenceId}. 204 · 400 tipo no marcable · 403 · 404
    @PostMapping("/{userId}/notifications/read")
    fun markNotificationRead(
        @PathVariable userId: UUID,
        @Valid @RequestBody request: NotificationReadRequest,
        @AuthenticationPrincipal auth: AuthenticatedUser
    ): ResponseEntity<Void> {
        AccessGuard.requireOwner(auth, userId)
        userService.markNotificationRead(userId, request.type!!.trim(), request.referenceId)
        return ResponseEntity.noContent().build()
    }

    /**
     * Protegido: solo el propio usuario. Multipart con las partes "front" y "back" (JPEG o PNG, máx. 5 MB cada una).
     * Guarda las imágenes, reemplaza las anteriores y devuelve sus ids, ya escritos en el usuario.
     * 200 · 400 archivo vacío, falta una parte o no es imagen · 403 · 404 · 413 demasiado grande
     */
    @PostMapping("/{userId}/dni-photos", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadDniPhotos(
        @PathVariable userId: UUID,
        @RequestPart("front") front: MultipartFile,
        @RequestPart("back") back: MultipartFile,
        @AuthenticationPrincipal auth: AuthenticatedUser
    ): ResponseEntity<DniPhotosResponse> {
        AccessGuard.requireOwner(auth, userId)
        return ResponseEntity.ok(dniPhotoService.upload(userId, front, back))
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
