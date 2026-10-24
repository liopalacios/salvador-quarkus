package com.join8.identifyr.controller

import com.join8.identifyr.model.dto.request.StabilityRequest
import com.join8.identifyr.model.dto.response.StabilityResponse
import com.join8.identifyr.service.StabilityService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/stability")
class StabilityController(
    private val stabilityService: StabilityService
) {

    @PostMapping("/{userId}")
    fun saveStability(
        @PathVariable userId: java.util.UUID,
        @Valid @RequestBody request: StabilityRequest
    ): ResponseEntity<StabilityResponse> {
        val response = stabilityService.saveStability(userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/{userId}")
    fun getStability(@PathVariable userId: java.util.UUID): ResponseEntity<StabilityResponse> {
        val response = stabilityService.getStabilityByUser(userId)
        return if (response != null) ResponseEntity.ok(response)
        else ResponseEntity.notFound().build()
    }
}