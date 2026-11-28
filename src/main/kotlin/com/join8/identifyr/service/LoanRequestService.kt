package com.join8.identifyr.service

import com.join8.identifyr.exception.BusinessException
import com.join8.identifyr.exception.ConflictException
import com.join8.identifyr.exception.ResourceNotFoundException
import com.join8.identifyr.model.dto.request.LoanRequestRegisterRequest
import com.join8.identifyr.model.dto.response.LoanRequestItemResponse
import com.join8.identifyr.model.dto.response.LoanRequestRegisterResponse
import com.join8.identifyr.model.dto.response.LoanRequestRejectResponse
import com.join8.identifyr.model.entity.DepositType
import com.join8.identifyr.model.entity.LoanCategory
import com.join8.identifyr.model.entity.LoanRequest
import com.join8.identifyr.model.entity.LoanRequestStatus
import com.join8.identifyr.model.entity.LoanStatus
import com.join8.identifyr.service.credit.Modality
import com.join8.identifyr.repository.LoanRequestRepository
import com.join8.identifyr.repository.UserRepository
import com.join8.identifyr.config.LoanProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.*

@Service
class LoanRequestService(
    private val loanRequestRepository: LoanRequestRepository,
    private val userRepository: UserRepository,
    private val loanRepository: com.join8.identifyr.repository.LoanRepository,
    private val loanProperties: LoanProperties,
    private val clock: Clock
) {

    /**
     * Registra una solicitud PENDIENTE. Formato y rangos ya los validó Bean Validation (400).
     * Usuario inexistente, solicitud PENDIENTE previa o una solicitud RECHAZADA -> [BusinessException] (422, nunca 404:
     * la app interpreta un 404 de este endpoint como "endpoint no publicado" y simula éxito).
     */
    @Transactional
    fun register(request: LoanRequestRegisterRequest): LoanRequestRegisterResponse {
        val userId = UUID.fromString(request.idUsuario!!)
        val category = LoanCategory.valueOf(request.categoria!!)
        val amount = request.montoSolicitado!!
        val frequency = request.frecuenciaCuota
        require(frequency == null || Modality.forPeriod(frequency) != null) {
            "frecuenciaCuota: solo se permite ${Modality.allowedPeriods.joinToString(" o ")}"
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
        // Con una solicitud rechazada el usuario ya no puede volver a solicitar
        if (loanRequestRepository.existsByUserIdAndStatus(userId, LoanRequestStatus.RECHAZADA)) {
            throw BusinessException(REJECTED_MESSAGE)
        }
        if (loanRepository.existsByUserIdAndStatus(userId, LoanStatus.VIGENTE)) {
            throw BusinessException("Ya tienes un préstamo vigente: cancélalo para solicitar otro")
        }
        // Tras cancelar un préstamo hay que esperar 48 h para solicitar otro
        loanRepository.findFirstByUserIdAndStatusOrderByClosedAtDesc(userId, LoanStatus.PAGADO)?.closedAt
            ?.plus(LoanService.NEW_LOAN_WAIT)
            ?.takeIf { clock.instant().isBefore(it) }
            ?.let { availableAt ->
                throw BusinessException("Podrás solicitar un nuevo préstamo desde el ${WAIT_FORMAT.format(availableAt.atZone(loanProperties.zone))}")
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
     * Listado del supervisor: solo las solicitudes del estado pedido, filtradas en SQL por la fecha de la
     * solicitud (created_at, día calendario en [LoanProperties.zone]):
     *  - sin [fecha]: los últimos [DEFAULT_DAYS] días, hoy incluido.
     *  - con [fecha] (yyyy-MM-dd): solo ese día. Formato inválido -> 400.
     * PENDIENTE: más antigua primero (orden de llegada). PROCESADA: última procesada primero (processed_at).
     * RECHAZADA: más reciente primero (updated_at). Sin resultados -> lista vacía.
     */
    @Transactional(readOnly = true)
    fun list(estado: String?, fecha: String? = null): List<LoanRequestItemResponse> {
        val status = parseStatus(estado)
        val zone = loanProperties.zone
        val (fromDay, toDay) = parseDate(fecha)?.let { it to it.plusDays(1) }
            ?: LocalDate.ofInstant(clock.instant(), zone).let { today -> today.minusDays(DEFAULT_DAYS - 1) to today.plusDays(1) }
        val from = fromDay.atStartOfDay(zone).toInstant()
        val to = toDay.atStartOfDay(zone).toInstant()

        val rows = when (status) {
            LoanRequestStatus.PENDIENTE -> loanRequestRepository.findPendingForList(status, from, to)
            LoanRequestStatus.PROCESADA -> loanRequestRepository.findProcessedForList(status, from, to)
            LoanRequestStatus.RECHAZADA -> loanRequestRepository.findRejectedForList(status, from, to)
        }
        return rows.map { it.toItem() }
    }

    /** "2026-11-28" (con trim). Vacío = sin filtro. Otro formato o fecha inexistente -> 400. */
    private fun parseDate(raw: String?): LocalDate? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException(INVALID_DATE_MESSAGE)
        }
    }

    /**
     * El supervisor rechaza una solicitud PENDIENTE. Motivo opcional (vacío = sin motivo).
     * Inexistente -> 404. Ya procesada o rechazada -> 409.
     */
    @Transactional
    fun reject(id: UUID, reason: String?, rejectedBy: String?): LoanRequestRejectResponse {
        // FOR UPDATE: no puede rechazarse mientras otro supervisor la procesa
        val request = loanRequestRepository.findByIdForUpdate(id) ?: throw ResourceNotFoundException("La solicitud no existe")
        if (request.status != LoanRequestStatus.PENDIENTE) {
            throw ConflictException("La solicitud no está PENDIENTE (estado actual: ${request.status})")
        }
        val now = LoanRequest.now()
        request.status = LoanRequestStatus.RECHAZADA
        request.rejectionReason = reason?.trim()?.takeIf { it.isNotEmpty() }
        request.rejectedAt = now
        request.rejectedBy = rejectedBy
        request.updatedAt = now
        loanRequestRepository.save(request)

        return LoanRequestRejectResponse(
            idSolicitud = request.id!!,
            estado = request.status.name,
            motivo = request.rejectionReason,
            fechaRechazo = now
        )
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
        carteraDigital = depositType.name,
        motivoRechazo = if (status == LoanRequestStatus.RECHAZADA) rejectionReason else null
    )

    companion object {
        const val INVALID_STATUS_MESSAGE = "estado debe ser PENDIENTE, PROCESADA o RECHAZADA"
        const val INVALID_DATE_MESSAGE = "fecha debe tener el formato yyyy-MM-dd"
        private val WAIT_FORMAT = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm")
        /** Rango del listado sin fecha: hoy y los 29 días anteriores. */
        const val DEFAULT_DAYS = 30L
        const val REJECTED_MESSAGE = "Tu solicitud de préstamo fue rechazada: no puedes registrar otra solicitud"
    }
}
