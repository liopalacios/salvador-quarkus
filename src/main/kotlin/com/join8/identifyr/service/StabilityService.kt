package com.join8.identifyr.service

import com.join8.identifyr.model.dto.request.StabilityRequest
import com.join8.identifyr.model.dto.response.StabilityResponse
import com.join8.identifyr.model.entity.DigitalStability
import com.join8.identifyr.repository.DigitalStabilityRepository
import com.join8.identifyr.repository.UserRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.util.*

@Service
class StabilityService(
    private val stabilityRepository: DigitalStabilityRepository,
    private val userRepository: UserRepository
) {

    @Transactional
    fun saveStability(userId: UUID, request: StabilityRequest): StabilityResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("Usuario no encontrado con ID: $userId") }

        if (stabilityRepository.existsByUser(user)) {
            throw IllegalStateException("El usuario ya tiene datos de estabilidad digital registrados")
        }

        val stability = DigitalStability(
            user = user,
            phoneAgeMonths = request.phoneAgeMonths,
            emailAgeMonths = request.emailAgeMonths,
            deviceTenureMonths = request.deviceTenureMonths,
            deviceConsistencyCount = request.deviceConsistencyCount,
            lastDeviceId = request.lastDeviceId
        )

        val saved = stabilityRepository.save(stability)

        return StabilityResponse(
            userId = saved.user.id!!,
            phoneAgeMonths = saved.phoneAgeMonths,
            emailAgeMonths = saved.emailAgeMonths,
            deviceTenureMonths = saved.deviceTenureMonths,
            deviceConsistencyCount = saved.deviceConsistencyCount
        )
    }

    fun getStabilityByUser(userId: UUID): StabilityResponse? {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("Usuario no encontrado con ID: $userId") }

        return stabilityRepository.findByUser(user)?.let { stability ->
            StabilityResponse(
                userId = stability.user.id!!,
                phoneAgeMonths = stability.phoneAgeMonths,
                emailAgeMonths = stability.emailAgeMonths,
                deviceTenureMonths = stability.deviceTenureMonths,
                deviceConsistencyCount = stability.deviceConsistencyCount
            )
        }
    }
}