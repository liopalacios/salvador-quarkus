package com.join8.identifyr.controller

import com.join8.identifyr.model.dto.request.StabilityRequest
import com.join8.identifyr.model.dto.response.StabilityResponse
import com.join8.identifyr.service.StabilityService
import jakarta.validation.Valid
import com.join8.identifyr.security.AccessGuard
import com.join8.identifyr.security.AuthenticatedUser
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/stability")
class StabilityController(
    private val stabilityService: StabilityService
) {

    @PostMapping("/{userId}")
    fun saveStability(
        @PathVariable userId: java.util.UUID,
        @Valid @RequestBody request: StabilityRequest,
        @AuthenticationPrincipal auth: AuthenticatedUser
    ): ResponseEntity<StabilityResponse> {
        AccessGuard.requireOwner(auth, userId)
        val response = stabilityService.saveStability(userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/{userId}")
    fun getStability(
        @PathVariable userId: java.util.UUID,
        @AuthenticationPrincipal auth: AuthenticatedUser
    ): ResponseEntity<StabilityResponse> {
        AccessGuard.requireOwnerOrSupervisor(auth, userId)
        val response = stabilityService.getStabilityByUser(userId)
        return if (response != null) ResponseEntity.ok(response)
        else ResponseEntity.notFound().build()
    }
}