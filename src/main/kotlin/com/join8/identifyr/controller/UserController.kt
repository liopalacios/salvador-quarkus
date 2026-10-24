package com.join8.identifyr.controller

import com.join8.identifyr.model.dto.request.UserRegisterRequest
import com.join8.identifyr.model.dto.response.UserRegisterResponse
import com.join8.identifyr.service.UserService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/users")
class UserController(private val userService: UserService) {
    @PostMapping("/register")
    fun registerUser(
        @Valid @RequestBody request: UserRegisterRequest
    ): ResponseEntity<UserRegisterResponse> {
        val response = userService.registerUser(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
    @GetMapping("/{id}")
    fun getUser(@PathVariable id: java.util.UUID): ResponseEntity<UserRegisterResponse> {
        val user = userService.findById(id)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(
            UserRegisterResponse(
                id = user.id!!,
                phone = user.phone,
                email = user.email,
                address = user.address,
                nationality = user.nationality,
                isVerified = user.isVerified
            )
        )
    }
}