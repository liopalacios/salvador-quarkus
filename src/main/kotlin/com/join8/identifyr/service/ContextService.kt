package com.join8.identifyr.service

import com.join8.identifyr.model.dto.request.ContextRequest
import com.join8.identifyr.model.dto.response.ContextResponse
import com.join8.identifyr.model.entity.UserContext
import com.join8.identifyr.repository.UserContextRepository
import com.join8.identifyr.repository.UserRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.util.*

@Service
class ContextService(
    private val contextRepository: UserContextRepository,
    private val userRepository: UserRepository
) {

    @Transactional
    fun saveContext(userId: UUID, request: ContextRequest): ContextResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("Usuario no encontrado con ID: $userId") }

        // Si ya existe contexto, actualizar (o lanzar error)
        if (contextRepository.existsByUser(user)) {
            throw IllegalStateException("El usuario ya tiene contexto registrado")
        }

        val context = UserContext(
            user = user,
            birthDate = request.birthDate,
            address = request.address,
            district = request.district,
            housingType = request.housingType?.name,
            educationLevel = request.educationLevel?.name,
            dependentsCount = request.dependentsCount,
            economicActivity = request.economicActivity,
            economicActivityType = request.economicActivityType?.name,
            economicActivityTenureMonths = request.economicActivityTenureMonths,
            residentialStabilityMonths = request.residentialStabilityMonths
        )

        val saved = contextRepository.save(context)

        return ContextResponse(
            userId = saved.user.id!!,
            birthDate = saved.birthDate,
            address = saved.address,
            district = saved.district,
            housingType = saved.housingType,
            educationLevel = saved.educationLevel,
            dependentsCount = saved.dependentsCount,
            economicActivity = saved.economicActivity,
            economicActivityType = saved.economicActivityType,
            economicActivityTenureMonths = saved.economicActivityTenureMonths,
            residentialStabilityMonths = saved.residentialStabilityMonths
        )
    }

    fun getContextByUser(userId: UUID): ContextResponse? {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("Usuario no encontrado con ID: $userId") }

        return contextRepository.findByUser(user)?.let { context ->
            ContextResponse(
                userId = context.user.id!!,
                birthDate = context.birthDate,
                address = context.address,
                district = context.district,
                housingType = context.housingType,
                educationLevel = context.educationLevel,
                dependentsCount = context.dependentsCount,
                economicActivity = context.economicActivity,
                economicActivityType = context.economicActivityType,
                economicActivityTenureMonths = context.economicActivityTenureMonths,
                residentialStabilityMonths = context.residentialStabilityMonths
            )
        }
    }
}