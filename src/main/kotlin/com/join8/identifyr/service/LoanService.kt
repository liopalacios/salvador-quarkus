package com.join8.identifyr.service

import com.join8.identifyr.config.LoanProperties
import com.join8.identifyr.exception.BusinessException
import com.join8.identifyr.exception.ConflictException
import com.join8.identifyr.exception.ResourceNotFoundException
import com.join8.identifyr.model.dto.request.LoanRegisterRequest
import com.join8.identifyr.model.dto.response.CurrentLoanResponse
import com.join8.identifyr.model.dto.response.CurrentLoanType
import com.join8.identifyr.model.dto.response.LoanRegisterResponse
import com.join8.identifyr.model.entity.*
import com.join8.identifyr.repository.LoanInstallmentRepository
import com.join8.identifyr.repository.LoanRepository
import com.join8.identifyr.repository.LoanRequestRepository
import com.join8.identifyr.service.credit.CreditEngine
import com.join8.identifyr.service.credit.CreditParameterService
import com.join8.identifyr.service.credit.CreditResponses
import com.join8.identifyr.service.credit.CreditSimulation
import com.join8.identifyr.service.credit.LoanSchedules
import com.join8.identifyr.service.credit.Modality
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*

@Service
class LoanService(
    private val loanRepository: LoanRepository,
    private val loanInstallmentRepository: LoanInstallmentRepository,
    private val loanRequestRepository: LoanRequestRepository,
    private val props: LoanProperties,
    private val clock: Clock,
    private val loanPaymentService: LoanPaymentService,
    private val creditParameterService: CreditParameterService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val MAX_OPERATION_CODE_LENGTH = 50
        private val PHONE_FORMAT = Regex("[0-9]{9}")
        /** Espera para volver a solicitar después de cancelar un préstamo. */
        val NEW_LOAN_WAIT: Duration = Duration.ofHours(48)
    }

    /**
     * "Guardar operación": crea el préstamo con el motor de crédito (CreditEngine, F01–F20) y la snapshot de los
     * parámetros vigentes, guarda el cronograma (cuota 1 ACTIVA) y pasa la solicitud a PROCESADA, todo en una
     * transacción. Del cliente solo se usan idSolicitud y codigoOperacion.
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

        val modality = Modality.forPeriod(loanRequest.installmentFrequency)
            ?: throw BusinessException(
                "La solicitud tiene una frecuencia de cuota inválida (${loanRequest.installmentFrequency}); " +
                    "se permite ${Modality.allowedPeriods.joinToString(" o ")} días"
            )
        if (loanRequest.requestedAmount <= 0) throw BusinessException("La solicitud tiene un monto inválido")

        // Prevalece la hora del servidor en que se registra la operación (nunca la del cliente ni la de la solicitud)
        val operationRegisteredAt = clock.instant().truncatedTo(ChronoUnit.SECONDS)
        val disbursementDate = LocalDate.ofInstant(operationRegisteredAt, props.zone)
        val params = creditParameterService.parametersAt(disbursementDate)
        val sim = CreditEngine.simulate(
            BigDecimal(loanRequest.requestedAmount), modality, disbursementDate, params, creditParameterService.holidays()
        )

        val loan = try {
            loanRepository.saveAndFlush(
                newLoan(loanRequest, operationCode, sim, createdBy, operationRegisteredAt)
                    .also { LoanSchedules.applySnapshot(it, sim, params) }
            )
        } catch (e: DataIntegrityViolationException) {
            // Carrera con otro préstamo que usó el mismo código entre el exists y el insert
            throw duplicatedOperationCode(operationCode)
        }

        val installments = loanInstallmentRepository.saveAllAndFlush(
            LoanSchedules.newInstallments(loan.id!!, sim, operationRegisteredAt)
        )

        loanRequest.status = LoanRequestStatus.PROCESADA
        loanRequest.operationCode = operationCode
        loanRequest.processedAt = operationRegisteredAt
        loanRequest.updatedAt = operationRegisteredAt
        loanRequestRepository.saveAndFlush(loanRequest)

        log.info("Préstamo {} registrado para la solicitud {} ({} x{}, cuota {})", loan.id, requestId, modality, modality.installments, sim.installmentAmount)

        return LoanRegisterResponse(
            idPrestamo = loan.id!!,
            idSolicitud = loan.loanRequestId,
            codigoOperacion = loan.operationCode,
            fechaRegistroOperacion = operationRegisteredAt.atZone(props.zone).toOffsetDateTime(),
            fechaDesembolso = loan.disbursementDate,
            montoDesembolsado = loan.amount,
            tasaInteres = CreditResponses.percent(sim.tea),
            montoInteres = sim.interestTotal,
            comisionTotal = sim.commissionTotal,
            apisTotal = sim.apisTotal,
            cuota = sim.installmentAmount,
            montoTotal = sim.totalToPay,
            tcea = CreditResponses.percent(sim.tcea),
            frecuenciaCuota = modality.periodDays,
            numeroCuotas = modality.installments,
            plazoDias = sim.termDays,
            cuotas = installments.sortedBy { it.installmentNumber }.map(CreditResponses::installment)
        )
    }

    /**
     * Situación actual de un celular, en este orden: préstamo VIGENTE (cuota ACTIVA, cronograma y cuánto paga hoy),
     * solicitud PENDIENTE, o según la última solicitud: RECHAZADA, o PROCESADA con su préstamo ya PAGADO. Si no -> 404.
     */
    @Transactional(readOnly = true)
    fun current(phone: String): CurrentLoanResponse {
        require(PHONE_FORMAT.matches(phone)) { "Celular debe tener 9 dígitos numéricos" }

        loanRepository.findFirstByPhoneAndStatusOrderByCreatedAtDesc(phone, LoanStatus.VIGENTE)?.let { loan ->
            val installments = loanInstallmentRepository.findAllByLoanIdOrderByInstallmentNumberAsc(loan.id!!)
            val active = installments.firstOrNull { it.status == InstallmentStatus.ACTIVA }
                ?: installments.firstOrNull { it.status != InstallmentStatus.PAGADA }
            if (active != null) {
                val today = LocalDate.ofInstant(clock.instant(), props.zone)
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
                    montoCuota = active.amount,
                    numeroCuota = active.installmentNumber,
                    totalCuotas = installments.size,
                    fechaVencimiento = active.dueDate,
                    estadoCuota = active.status.name,
                    cuotas = installments.map(CreditResponses::installment),
                    pagoPendiente = loanPaymentService.pendingFor(loan.id!!),
                    cotizacionHoy = CreditResponses.paymentQuote(
                        today,
                        LoanSchedules.quoteActive(loan, installments, today)?.second,
                        LoanSchedules.quotePayoff(loan, installments, today)?.second
                    ),
                    tea = loan.tea?.let(CreditResponses::percent),
                    tcea = loan.tcea?.let(CreditResponses::percent)
                )
            }
        }

        val pending = loanRequestRepository.findFirstByPhoneAndStatusOrderByCreatedAtDesc(phone, LoanRequestStatus.PENDIENTE)
            ?: return fromLastRequest(phone)
        return CurrentLoanResponse(
            tipo = CurrentLoanType.SOLICITUD,
            idSolicitud = pending.id!!,
            estado = pending.status.name,
            carteraDigital = pending.depositType.name,
            montoSolicitado = pending.requestedAmount,
            frecuenciaCuota = pending.installmentFrequency
        )
    }

    // Según la ÚLTIMA solicitud: rechazada, o procesada con su préstamo ya pagado (puede volver a solicitar tras 48 h)
    private fun fromLastRequest(phone: String): CurrentLoanResponse {
        val last = loanRequestRepository.findFirstByPhoneOrderByCreatedAtDesc(phone)
        if (last?.status == LoanRequestStatus.RECHAZADA) {
            return CurrentLoanResponse(
                tipo = CurrentLoanType.SOLICITUD,
                idSolicitud = last.id!!,
                estado = last.status.name,
                carteraDigital = last.depositType.name,
                montoSolicitado = last.requestedAmount,
                frecuenciaCuota = last.installmentFrequency,
                motivoRechazo = last.rejectionReason
            )
        }
        val paid = last?.takeIf { it.status == LoanRequestStatus.PROCESADA }
            ?.let { loanRepository.findByLoanRequestId(it.id!!) }
            ?.takeIf { it.status == LoanStatus.PAGADO }
            ?: throw ResourceNotFoundException("No hay préstamo activo ni solicitud pendiente para el celular")
        val closedAt = paid.closedAt ?: paid.updatedAt
        return CurrentLoanResponse(
            tipo = CurrentLoanType.PRESTAMO_PAGADO,
            idSolicitud = paid.loanRequestId,
            estado = paid.status.name,
            carteraDigital = paid.depositType.name,
            frecuenciaCuota = paid.installmentFrequency,
            idPrestamo = paid.id!!,
            codigoOperacion = paid.operationCode,
            fechaDesembolso = paid.disbursementDate,
            montoDesembolsado = paid.amount,
            montoTotal = paid.totalAmount,
            saldoPendiente = BigDecimal.ZERO.setScale(2),
            totalCuotas = paid.installmentCount,
            fechaCancelacion = closedAt,
            nuevaSolicitudDesde = closedAt.plus(NEW_LOAN_WAIT)
        )
    }

    private fun newLoan(
        loanRequest: LoanRequest, operationCode: String, sim: CreditSimulation, createdBy: String?, now: Instant
    ) = Loan(
        loanRequestId = loanRequest.id!!,
        userId = loanRequest.userId,
        phone = loanRequest.phone,
        amount = sim.amount,
        interest = sim.interestTotal,
        interestRate = sim.tea,
        totalAmount = sim.totalToPay,
        installmentFrequency = sim.modality.periodDays,
        installmentCount = sim.modality.installments,
        termDays = sim.termDays,
        operationCode = operationCode,
        score = loanRequest.score,
        category = loanRequest.category,
        depositType = loanRequest.depositType,
        operationRegisteredAt = now,
        disbursementDate = sim.disbursementDate,
        requestDate = loanRequest.createdAt,
        paymentDate = sim.finalDueDate,
        appPaymentDate = sim.finalDueDate,
        outstandingBalance = sim.totalToPay,
        createdBy = createdBy,
        createdAt = now
    )

    private fun duplicatedOperationCode(code: String) =
        ConflictException("El codigoOperacion '$code' ya está registrado en otro préstamo")
}
