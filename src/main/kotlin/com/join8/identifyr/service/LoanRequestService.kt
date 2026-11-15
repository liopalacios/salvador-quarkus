package com.join8.identifyr.service

import com.join8.identifyr.exception.BusinessException
import com.join8.identifyr.model.dto.request.LoanRequestRegisterRequest
import com.join8.identifyr.model.dto.response.LoanRequestItemResponse
import com.join8.identifyr.model.dto.response.LoanRequestRegisterResponse
import com.join8.identifyr.model.entity.DepositType
import com.join8.identifyr.model.entity.LoanCategory
import com.join8.identifyr.model.entity.LoanRequest
import com.join8.identifyr.model.entity.LoanRequestStatus
import com.join8.identifyr.repository.LoanRequestRepository
import com.join8.identifyr.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class LoanRequestService(
    private val loanRequestRepository: LoanRequestRepository,
    private val userRepository: UserRepository
) {

    /**
     * Registra una solicitud PENDIENTE. Formato y rangos ya los validó Bean Validation (400).
     * Usuario inexistente o solicitud PENDIENTE previa -> [BusinessException] (422, nunca 404:
     * la app interpreta un 404 de este endpoint como "endpoint no publicado" y simula éxito).
     */
    @Transactional
    fun register(request: LoanRequestRegisterRequest): LoanRequestRegisterResponse {
        val userId = UUID.fromString(request.idUsuario!!)
        val category = LoanCategory.valueOf(request.categoria!!)
        val amount = request.montoSolicitado!!
        val frequency = request.frecuenciaCuota
        require(frequency == null || LoanPlan.forFrequency(frequency) != null) {
            "frecuenciaCuota: solo se permite ${LoanPlan.allowedFrequencies.joinToString(" o ")}"
        }

        // Tope según el nivel de score enviado (misma tabla que usa GET /scores/{userId})
        val maxAmount = ScoreLevel.entries.single { it.levelName == category.name }.maxLoanAmount
        require(amount <= maxAmount) {
            "montoSolicitado: no puede superar S/ $maxAmount para la categoría ${category.name}"
        }

        // Bloquea al usuario: dos solicitudes simultáneas no pueden quedar ambas PENDIENTE
        userRepository.findByIdForUpdate(userId)
            ?: throw BusinessException("El usuario $userId no existe")

        if (loanRequestRepository.existsByUserIdAndStatus(userId, LoanRequestStatus.PENDIENTE)) {
            throw BusinessException("El usuario ya tiene una solicitud de préstamo pendiente")
        }

        val saved = loanRequestRepository.saveAndFlush(
            LoanRequest(
                userId = userId,
                phone = request.numCelular!!,
                depositType = DepositType.valueOf(request.tipoDeposito!!),
                category = category,
                score = request.puntaje!!,
                requestedAmount = amount,
                installmentFrequency = frequency
            )
        )
        return LoanRequestRegisterResponse(id = saved.id!!, status = saved.status.name, createdAt = saved.createdAt)
    }

    /**
     * Listado del supervisor: solo las solicitudes del estado pedido, filtradas en SQL.
     * PENDIENTE: más antigua primero (orden de llegada). PROCESADA: última procesada primero (processed_at).
     * RECHAZADA: más reciente primero (updated_at; no hay fecha de rechazo). Sin resultados -> lista vacía.
     */
    @Transactional(readOnly = true)
    fun list(estado: String?): List<LoanRequestItemResponse> {
        val rows = when (val status = parseStatus(estado)) {
            LoanRequestStatus.PENDIENTE -> loanRequestRepository.findAllByStatusOrderByCreatedAtAscIdAsc(status)
            LoanRequestStatus.PROCESADA -> loanRequestRepository.findAllByStatusOrderByProcessedAtDescIdAsc(status)
            LoanRequestStatus.RECHAZADA -> loanRequestRepository.findAllByStatusOrderByUpdatedAtDescIdAsc(status)
        }
        return rows.map { it.toItem() }
    }

    /** Sin distinguir mayúsculas y con trim: " pendiente " -> PENDIENTE. Faltante u otro valor -> 400. */
    private fun parseStatus(raw: String?): LoanRequestStatus {
        val normalized = raw?.trim()?.uppercase(Locale.ROOT)
        return LoanRequestStatus.entries.firstOrNull { it.name == normalized }
            ?: throw IllegalArgumentException(INVALID_STATUS_MESSAGE)
    }

    private fun LoanRequest.toItem() = LoanRequestItemResponse(
        idSolicitud = id!!,
        numCelular = phone,
        montoSolicitado = requestedAmount,
        puntaje = score,
        categoria = category.name,
        estado = status.name,
        codigoOperacion = if (status == LoanRequestStatus.PROCESADA) operationCode else null,
        carteraDigital = depositType.name
    )

    companion object {
        const val INVALID_STATUS_MESSAGE = "estado debe ser PENDIENTE, PROCESADA o RECHAZADA"
    }
}
