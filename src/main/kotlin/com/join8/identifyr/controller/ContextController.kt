package com.join8.identifyr.controller

import com.join8.identifyr.model.dto.request.ContextRequest
import com.join8.identifyr.model.dto.response.ContextResponse
import com.join8.identifyr.service.ContextService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/context")
class ContextController(
    private val contextService: ContextService
) {

    @PostMapping("/{userId}")
    fun saveContext(
        @PathVariable userId: java.util.UUID,
        @Valid @RequestBody request: ContextRequest
    ): ResponseEntity<ContextResponse> {
        val response = contextService.saveContext(userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/{userId}")
    fun getContext(@PathVariable userId: java.util.UUID): ResponseEntity<ContextResponse> {
        val response = contextService.getContextByUser(userId)
        return if (response != null) ResponseEntity.ok(response)
        else ResponseEntity.notFound().build()
    }
}