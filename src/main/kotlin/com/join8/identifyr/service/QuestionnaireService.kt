package com.join8.identifyr.service

import com.join8.identifyr.exception.ResourceNotFoundException
import com.join8.identifyr.model.dto.request.QuestionnaireRegisterRequest
import com.join8.identifyr.model.dto.response.QuestionnaireRegisterResponse
import com.join8.identifyr.model.dto.response.ScoreResponse
import com.join8.identifyr.model.entity.Questionnaire
import com.join8.identifyr.repository.QuestionnaireRepository
import com.join8.identifyr.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class QuestionnaireService(
    private val questionnaireRepository: QuestionnaireRepository,
    private val userRepository: UserRepository,
    private val scoreService: ScoreService
) {

    companion object {
        val EDUCATION_LEVELS = listOf("Primaria", "Secundaria", "Técnico", "Universitario", "Postgrado", "Ninguno")
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        private val RUC_REGEX = Regex("^\\d{11}$")
    }

    /**
     * Registra un envío del cuestionario. Nunca modifica respuestas: si hay un registro activo,
     * lo pasa a 'I' e inserta uno nuevo con los valores anteriores combinados con los del request.
     * Todo en una transacción; ante cualquier error se hace rollback.
     */
    @Transactional
    fun register(request: QuestionnaireRegisterRequest): QuestionnaireRegisterResponse {
        val userId = parseUserId(request.userId)
        val answers = validate(request)

        // Bloquea al usuario: dos envíos simultáneos no pueden crear dos registros activos
        userRepository.findByIdForUpdate(userId)
            ?: throw ResourceNotFoundException("El usuario $userId no existe")

        val previous = questionnaireRepository.findByUserIdAndEstado(userId, Questionnaire.ACTIVE)
        if (previous == null) {
            val missing = answers.missingBlock1()
            require(missing.isEmpty()) {
                "Faltan respuestas obligatorias en el primer registro: ${missing.joinToString()}"
            }
        } else {
            previous.estado = Questionnaire.INACTIVE
            previous.updatedAt = Questionnaire.now()
            // flush antes del insert: el índice único parcial no admite dos filas 'A' a la vez
            questionnaireRepository.saveAndFlush(previous)
        }

        val saved = questionnaireRepository.saveAndFlush(answers.mergeOnto(userId, previous))
        return QuestionnaireRegisterResponse(
            questionnaireId = saved.id!!,
            userId = saved.userId,
            status = saved.estado,
            createdAt = saved.createdAt
        )
    }

    /** Score calculado con el registro activo guardado en la base de datos (sin caché). */
    @Transactional(readOnly = true)
    fun scoreFor(userId: UUID): ScoreResponse {
        if (!userRepository.existsById(userId)) throw ResourceNotFoundException("El usuario $userId no existe")
        val active = questionnaireRepository.findByUserIdAndEstado(userId, Questionnaire.ACTIVE)
            ?: throw ResourceNotFoundException("El usuario $userId no tiene un cuestionario registrado")
        return scoreService.calculate(active)
    }

    private fun parseUserId(raw: String?): UUID {
        require(!raw.isNullOrBlank()) { "El userId es obligatorio" }
        return try {
            UUID.fromString(raw.trim())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("El userId '$raw' no es un UUID válido")
        }
    }

    private fun validate(request: QuestionnaireRegisterRequest): Answers {
        fun String?.clean() = this?.trim()?.takeIf { it.isNotEmpty() }
        fun code(field: String, value: String?) = value.clean()?.let { scoreService.normalizeCode(field, it) }

        val answers = Answers(
            employmentType = code("employmentType", request.employmentType),
            employmentTenure = code("employmentTenure", request.employmentTenure),
            residentialTenure = code("residentialTenure", request.residentialTenure),
            housingType = code("housingType", request.housingType),
            dependents = code("dependents", request.dependents),
            email = request.email.clean(),
            address = request.address.clean(),
            ruc = request.ruc.clean(),
            driverLicense = request.driverLicense.clean(),
            vehiclePlate = request.vehiclePlate.clean()?.uppercase(),
            educationLevel = request.educationLevel.clean(),
            workplace = request.workplace.clean(),
            workApps = request.workApps.clean(),
            accountStatementPhotoId = request.accountStatementPhotoId.clean()
        )

        require(!answers.isEmpty()) { "El request no trae ninguna respuesta además del userId" }
        answers.email?.let { require(EMAIL_REGEX.matches(it)) { "El email '$it' no tiene un formato válido" } }
        answers.ruc?.let { require(RUC_REGEX.matches(it)) { "El RUC debe tener 11 dígitos" } }
        answers.educationLevel?.let {
            require(it in EDUCATION_LEVELS) {
                "Nivel de estudios inválido '$it'. Valores permitidos: ${EDUCATION_LEVELS.joinToString()}"
            }
        }
        return answers
    }

    /** Valores ya limpios del request; null = el campo no vino y conserva el valor anterior. */
    private data class Answers(
        val employmentType: String?,
        val employmentTenure: String?,
        val residentialTenure: String?,
        val housingType: String?,
        val dependents: String?,
        val email: String?,
        val address: String?,
        val ruc: String?,
        val driverLicense: String?,
        val vehiclePlate: String?,
        val educationLevel: String?,
        val workplace: String?,
        val workApps: String?,
        val accountStatementPhotoId: String?
    ) {
        fun isEmpty() = listOf(
            employmentType, employmentTenure, residentialTenure, housingType, dependents,
            email, address, ruc, driverLicense, vehiclePlate, educationLevel, workplace, workApps,
            accountStatementPhotoId
        ).all { it == null }

        fun missingBlock1() = listOf(
            "employmentType" to employmentType,
            "employmentTenure" to employmentTenure,
            "residentialTenure" to residentialTenure,
            "housingType" to housingType,
            "dependents" to dependents
        ).filter { it.second == null }.map { it.first }

        fun mergeOnto(userId: UUID, previous: Questionnaire?) = Questionnaire(
            userId = userId,
            employmentType = employmentType ?: previous?.employmentType,
            employmentTenure = employmentTenure ?: previous?.employmentTenure,
            residentialTenure = residentialTenure ?: previous?.residentialTenure,
            housingType = housingType ?: previous?.housingType,
            dependents = dependents ?: previous?.dependents,
            email = email ?: previous?.email,
            address = address ?: previous?.address,
            ruc = ruc ?: previous?.ruc,
            driverLicense = driverLicense ?: previous?.driverLicense,
            vehiclePlate = vehiclePlate ?: previous?.vehiclePlate,
            educationLevel = educationLevel ?: previous?.educationLevel,
            workplace = workplace ?: previous?.workplace,
            workApps = workApps ?: previous?.workApps,
            accountStatementPhotoId = accountStatementPhotoId ?: previous?.accountStatementPhotoId,
            estado = Questionnaire.ACTIVE
        )
    }
}
