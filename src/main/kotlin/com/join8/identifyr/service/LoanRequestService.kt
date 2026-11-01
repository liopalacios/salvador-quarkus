package com.join8.identifyr.service

import com.join8.identifyr.exception.BusinessException
import com.join8.identifyr.model.dto.request.LoanRequestRegisterRequest
import com.join8.identifyr.model.dto.response.LoanRequestItemResponse
import com.join8.identifyr.model.dto.response.LoanRequestListResponse
import com.join8.identifyr.model.dto.response.LoanRequestRegisterResponse
import com.join8.identifyr.model.entity.DepositType
import com.join8.identifyr.model.entity.LoanCategory
import com.join8.identifyr.model.entity.LoanRequest
import com.join8.identifyr.model.entity.LoanRequestStatus
import com.join8.identifyr.repository.LoanRequestRepository
import com.join8.identifyr.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
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
                requestedAmount = amount
            )
        )
        return LoanRequestRegisterResponse(id = saved.id!!, status = saved.status.name, createdAt = saved.createdAt)
    }

    /**
     * Listado del supervisor. PENDIENTE: más antigua primero (created_at asc), para atender primero
     * la que más espera. PROCESADA / RECHAZADA: más reciente primero (updated_at desc).
     * El id desempata para que la paginación sea estable.
     */
    @Transactional(readOnly = true)
    fun list(estado: String?, page: Int, size: Int): LoanRequestListResponse {
        val status = parseStatus(estado)
        require(page >= 0) { "page debe ser mayor o igual a 0" }
        require(size in 1..MAX_PAGE_SIZE) { "size debe estar entre 1 y $MAX_PAGE_SIZE" }

        val sort = when (status) {
            LoanRequestStatus.PENDIENTE -> Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))
            else -> Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id"))
        }
        val result = loanRequestRepository.findAllByStatus(status, PageRequest.of(page, size, sort))

        val counts = loanRequestRepository.countGroupByStatus().associate { it.status to it.total }
        return LoanRequestListResponse(
            items = result.content.map { it.toItem() },
            page = page,
            size = size,
            totalItems = result.totalElements,
            totalPages = result.totalPages,
            conteos = LoanRequestStatus.entries.associate { it.name to (counts[it] ?: 0L) }
        )
    }

    private fun parseStatus(raw: String?): LoanRequestStatus {
        val allowed = LoanRequestStatus.entries.joinToString(", ")
        require(!raw.isNullOrBlank()) { "El parámetro estado es obligatorio. Valores permitidos: $allowed" }
        return LoanRequestStatus.entries.firstOrNull { it.name == raw }
            ?: throw IllegalArgumentException("estado inválido '$raw'. Valores permitidos: $allowed")
    }

    private fun LoanRequest.toItem() = LoanRequestItemResponse(
        id = id!!,
        idUsuario = userId,
        numCelular = phone,
        montoSolicitado = requestedAmount,
        puntaje = score,
        categoria = category.name,
        tipoDeposito = depositType.name,
        estado = status.name,
        codigoOperacion = operationCode,
        fechaRegistro = createdAt,
        fechaActualizacion = updatedAt
    )

    companion object {
        const val MAX_PAGE_SIZE = 100
    }
}
