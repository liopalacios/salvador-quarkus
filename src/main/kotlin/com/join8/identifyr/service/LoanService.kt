package com.join8.identifyr.service

import com.join8.identifyr.config.LoanProperties
import com.join8.identifyr.exception.BusinessException
import com.join8.identifyr.exception.ConflictException
import com.join8.identifyr.exception.ResourceNotFoundException
import com.join8.identifyr.model.dto.request.LoanRegisterRequest
import com.join8.identifyr.model.dto.response.CurrentLoanResponse
import com.join8.identifyr.model.dto.response.CurrentLoanType
import com.join8.identifyr.model.dto.response.LoanInstallmentResponse
import com.join8.identifyr.model.dto.response.LoanRegisterResponse
import com.join8.identifyr.model.entity.*
import com.join8.identifyr.repository.LoanInstallmentRepository
import com.join8.identifyr.repository.LoanRepository
import com.join8.identifyr.repository.LoanRequestRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

@Service
class LoanService(
    private val loanRepository: LoanRepository,
    private val loanInstallmentRepository: LoanInstallmentRepository,
    private val loanRequestRepository: LoanRequestRepository,
    private val props: LoanProperties,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val MAX_OPERATION_CODE_LENGTH = 50
        private val PHONE_FORMAT = Regex("[0-9]{9}")
    }

    /**
     * "Guardar operación": crea el préstamo y su cronograma y pasa la solicitud a PROCESADA, todo en una
     * transacción (si algo falla no queda nada a medias). Del cliente solo se usan idSolicitud y codigoOperacion.
     *
     * 404 solicitud inexistente · 409 no PENDIENTE o código repetido · 422 datos de la solicitud inválidos.
     */
    @Transactional
    fun register(request: LoanRegisterRequest, createdBy: String?): LoanRegisterResponse {
        val requestId = UUID.fromString(request.idSolicitud!!)
        val operationCode = request.codigoOperacion!!.trim()
        require(operationCode.length <= MAX_OPERATION_CODE_LENGTH) {
            "codigoOperacion: no puede tener más de $MAX_OPERATION_CODE_LENGTH caracteres"
        }

        // FOR UPDATE: dos registros simultáneos de la misma solicitud se serializan; el segundo ve PROCESADA
        val loanRequest = loanRequestRepository.findByIdForUpdate(requestId)
            ?: throw ResourceNotFoundException("La solicitud $requestId no existe")

        if (loanRequest.status != LoanRequestStatus.PENDIENTE) {
            throw ConflictException(
                "La solicitud no está PENDIENTE (estado actual: ${loanRequest.status})",
                idPrestamo = loanRepository.findByLoanRequestId(requestId)?.id
            )
        }
        if (loanRepository.existsByOperationCode(operationCode)) throw duplicatedOperationCode(operationCode)

        val plan = LoanPlan.forFrequency(loanRequest.installmentFrequency)
            ?: throw BusinessException(
                "La solicitud tiene una frecuencia de cuota inválida (${loanRequest.installmentFrequency}); " +
                    "se permite ${LoanPlan.allowedFrequencies.joinToString(" o ")} días"
            )
        if (loanRequest.requestedAmount <= 0) throw BusinessException("La solicitud tiene un monto inválido")

        // Prevalece la hora del servidor en que se registra la operación (nunca la del cliente ni la de la solicitud)
        val operationRegisteredAt = clock.instant().truncatedTo(ChronoUnit.SECONDS)
        val disbursementDate = LoanScheduleCalculator.disbursementDate(operationRegisteredAt, props.zone)
        val schedule = LoanScheduleCalculator.calculate(loanRequest.requestedAmount, plan, disbursementDate)

        val loan = try {
            loanRepository.saveAndFlush(newLoan(loanRequest, operationCode, schedule, createdBy, operationRegisteredAt))
        } catch (e: DataIntegrityViolationException) {
            // Carrera con otro préstamo que usó el mismo código entre el exists y el insert
            throw duplicatedOperationCode(operationCode)
        }

        val installments = loanInstallmentRepository.saveAllAndFlush(schedule.installments.map {
            LoanInstallment(
                loanId = loan.id!!, installmentNumber = it.number, dueDate = it.dueDate,
                amount = it.amount, createdAt = operationRegisteredAt
            )
        })

        loanRequest.status = LoanRequestStatus.PROCESADA
        loanRequest.operationCode = operationCode
        loanRequest.processedAt = operationRegisteredAt
        loanRequest.updatedAt = operationRegisteredAt
        loanRequestRepository.saveAndFlush(loanRequest)

        log.info("Préstamo {} registrado para la solicitud {} ({} x{})", loan.id, requestId, plan, plan.installmentCount)

        return LoanRegisterResponse(
            idPrestamo = loan.id!!,
            idSolicitud = loan.loanRequestId,
            codigoOperacion = loan.operationCode,
            fechaRegistroOperacion = operationRegisteredAt.atZone(props.zone).toOffsetDateTime(),
            fechaDesembolso = loan.disbursementDate,
            montoDesembolsado = loan.amount,
            tasaInteres = LoanScheduleCalculator.basisPointsToPercent(plan.interestRateBp),
            montoInteres = loan.interest,
            montoTotal = loan.totalAmount,
            frecuenciaCuota = loan.installmentFrequency,
            numeroCuotas = loan.installmentCount,
            plazoDias = loan.termDays,
            cuotas = installments.sortedBy { it.installmentNumber }.map {
                LoanInstallmentResponse(it.installmentNumber, it.dueDate, it.amount, it.status.name)
            }
        )
    }

    /**
     * Situación actual de un celular: préstamo VIGENTE (con la próxima cuota sin pagar) o, si no hay,
     * solicitud PENDIENTE. Sin ninguno de los dos -> 404.
     */
    @Transactional(readOnly = true)
    fun current(phone: String): CurrentLoanResponse {
        require(PHONE_FORMAT.matches(phone)) { "Celular debe tener 9 dígitos numéricos" }

        loanRepository.findFirstByPhoneAndStatusOrderByCreatedAtDesc(phone, LoanStatus.VIGENTE)?.let { loan ->
            val installments = loanInstallmentRepository.findAllByLoanIdOrderByInstallmentNumberAsc(loan.id!!)
            val next = installments.firstOrNull { it.status != InstallmentStatus.PAGADA }
            if (next != null) {
                return CurrentLoanResponse(
                    tipo = CurrentLoanType.PRESTAMO_ACTIVO,
                    idSolicitud = loan.loanRequestId,
                    estado = loan.status.name,
                    carteraDigital = loan.depositType.name,
                    frecuenciaCuota = loan.installmentFrequency,
                    idPrestamo = loan.id!!,
                    codigoOperacion = loan.operationCode,
                    fechaDesembolso = loan.disbursementDate,
                    montoDesembolsado = loan.amount,
                    montoTotal = loan.totalAmount,
                    saldoPendiente = loan.outstandingBalance,
                    montoCuota = next.amount,
                    numeroCuota = next.installmentNumber,
                    totalCuotas = installments.size,
                    fechaVencimiento = next.dueDate,
                    estadoCuota = next.status.name
                )
            }
        }

        val pending = loanRequestRepository.findFirstByPhoneAndStatusOrderByCreatedAtDesc(phone, LoanRequestStatus.PENDIENTE)
            ?: throw ResourceNotFoundException("No hay préstamo activo ni solicitud pendiente para el celular")
        return CurrentLoanResponse(
            tipo = CurrentLoanType.SOLICITUD,
            idSolicitud = pending.id!!,
            estado = pending.status.name,
            carteraDigital = pending.depositType.name,
            montoSolicitado = pending.requestedAmount,
            frecuenciaCuota = pending.installmentFrequency
        )
    }

    private fun newLoan(
        loanRequest: LoanRequest, operationCode: String, schedule: LoanSchedule, createdBy: String?, now: Instant
    ) = Loan(
        loanRequestId = loanRequest.id!!,
        userId = loanRequest.userId,
        phone = loanRequest.phone,
        amount = schedule.principal,
        interest = schedule.interest,
        interestRate = LoanScheduleCalculator.basisPointsToFraction(schedule.plan.interestRateBp),
        totalAmount = schedule.total,
        installmentFrequency = schedule.plan.frequencyDays,
        installmentCount = schedule.plan.installmentCount,
        termDays = LoanPlan.TERM_DAYS,
        operationCode = operationCode,
        score = loanRequest.score,
        category = loanRequest.category,
        depositType = loanRequest.depositType,
        operationRegisteredAt = now,
        disbursementDate = schedule.disbursementDate,
        requestDate = loanRequest.createdAt,
        paymentDate = schedule.finalDueDate,
        appPaymentDate = schedule.finalDueDate,
        outstandingBalance = schedule.total,
        createdBy = createdBy,
        createdAt = now
    )

    private fun duplicatedOperationCode(code: String) =
        ConflictException("El codigoOperacion '$code' ya está registrado en otro préstamo")
}
