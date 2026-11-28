package com.join8.identifyr.service

import com.join8.identifyr.config.LoanProperties
import com.join8.identifyr.exception.BusinessException
import com.join8.identifyr.exception.ConflictException
import com.join8.identifyr.exception.ResourceNotFoundException
import com.join8.identifyr.model.dto.response.LoanPaymentResponse
import com.join8.identifyr.model.entity.*
import com.join8.identifyr.repository.LoanInstallmentRepository
import com.join8.identifyr.repository.LoanPaymentItemRepository
import com.join8.identifyr.repository.LoanPaymentRepository
import com.join8.identifyr.repository.LoanRepository
import com.join8.identifyr.service.credit.InstallmentQuote
import com.join8.identifyr.service.credit.LoanSchedules
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.*

@Service
class LoanPaymentService(
    private val loanRepository: LoanRepository,
    private val loanInstallmentRepository: LoanInstallmentRepository,
    private val loanPaymentRepository: LoanPaymentRepository,
    private val loanPaymentItemRepository: LoanPaymentItemRepository,
    private val props: LoanProperties,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * El cliente informa un pago con su código de operación. El monto lo liquida el motor de crédito
     * (F21–F28) con fecha de pago = hoy en Lima, y queda fijo aunque el supervisor confirme otro día:
     *  - CUOTA: la cuota ACTIVA (anticipada: interés solo por los días usados; con atraso: interés adicional + mora).
     *  - TOTAL: todas las cuotas no pagadas (F28).
     * Queda PENDIENTE: el préstamo no cambia hasta que el supervisor lo confirme.
     * 404 préstamo inexistente o de otro usuario · 409 ya hay un pago pendiente o el código ya se usó ·
     * 422 préstamo no vigente o sin cuotas por pagar.
     */
    @Transactional
    fun register(loanId: UUID, userId: UUID, type: PaymentType, rawOperationCode: String): LoanPaymentResponse {
        val operationCode = rawOperationCode.trim()
        require(operationCode.isNotEmpty()) { "codigoOperacion es obligatorio" }

        val loan = loanRepository.findByIdForUpdate(loanId)?.takeIf { it.userId == userId }
            ?: throw ResourceNotFoundException("El préstamo no existe")
        if (loan.status != LoanStatus.VIGENTE) throw BusinessException("El préstamo no está vigente")
        if (loanPaymentRepository.findFirstByLoanIdAndStatus(loanId, PaymentStatus.PENDIENTE) != null) {
            throw ConflictException("Ya tienes un pago pendiente de confirmación")
        }
        if (loanPaymentRepository.existsByOperationCodeAndStatusNot(operationCode, PaymentStatus.RECHAZADO)) {
            throw ConflictException("El código de operación ya fue registrado")
        }

        val installments = loanInstallmentRepository.findAllByLoanIdOrderByInstallmentNumberAsc(loanId)
        if (installments.none { it.status != InstallmentStatus.PAGADA }) {
            throw BusinessException("El préstamo no tiene cuotas por pagar")
        }
        val paymentDate = LocalDate.ofInstant(clock.instant(), props.zone)
        val quoted: List<Pair<LoanInstallment, InstallmentQuote>> = when (type) {
            PaymentType.CUOTA -> listOfNotNull(LoanSchedules.quoteActive(loan, installments, paymentDate))
            PaymentType.TOTAL -> LoanSchedules.quotePayoff(loan, installments, paymentDate)
                ?.let { (unpaid, payoff) -> unpaid.zip(payoff.items) }.orEmpty()
        }
        if (quoted.isEmpty()) throw BusinessException("El préstamo no tiene un cronograma calculado; contacta a soporte")

        fun sum(f: (InstallmentQuote) -> BigDecimal) = quoted.fold(BigDecimal.ZERO.setScale(2)) { acc, (_, q) -> acc.add(f(q)) }
        val saved = loanPaymentRepository.saveAndFlush(
            LoanPayment(
                loanId = loanId, userId = userId, type = type,
                installmentId = if (type == PaymentType.CUOTA) quoted.single().first.id else null,
                amount = sum { it.amountToPay }, operationCode = operationCode, paymentDate = paymentDate,
                interest = sum { it.interestToPay }, moratorium = sum { it.moratorium }, principal = sum { it.principal },
                commission = sum { it.commission }, apis = sum { it.apis }
            )
        )
        loanPaymentItemRepository.saveAll(quoted.map { (installment, q) ->
            LoanPaymentItem(
                paymentId = saved.id!!, installmentId = installment.id, installmentNumber = q.number, timing = q.timing,
                usedDays = q.usedDays, lateDays = q.lateDays, interest = q.interestToPay, moratorium = q.moratorium,
                principal = q.principal, commission = q.commission, apis = q.apis, amount = q.amountToPay
            )
        })
        log.info("Pago {} {} de S/ {} registrado para el préstamo {} (pendiente de confirmación)", saved.id, type, saved.amount, loanId)
        return saved.toResponse()
    }

    /** Pago pendiente del préstamo (para el inicio del cliente); null si no hay. */
    @Transactional(readOnly = true)
    fun pendingFor(loanId: UUID): LoanPaymentResponse? =
        loanPaymentRepository.findFirstByLoanIdAndStatus(loanId, PaymentStatus.PENDIENTE)?.toResponse()

    /** Listado del supervisor por estado (PENDIENTE por defecto). Estado inválido -> 400. */
    @Transactional(readOnly = true)
    fun list(estado: String?): List<LoanPaymentResponse> {
        val status = estado?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(Locale.ROOT)
            ?.let { value -> PaymentStatus.entries.firstOrNull { it.name == value } ?: throw IllegalArgumentException(INVALID_STATUS) }
            ?: PaymentStatus.PENDIENTE
        val payments = if (status == PaymentStatus.PENDIENTE) {
            loanPaymentRepository.findAllByStatusOrderByCreatedAtAscIdAsc(status)
        } else {
            loanPaymentRepository.findAllByStatusOrderByReviewedAtDescIdAsc(status)
        }
        val phones = loanRepository.findAllById(payments.map { it.loanId }.toSet()).associate { it.id to it.phone }
        return payments.map { it.toResponse(phone = phones[it.loanId]) }
    }

    /**
     * El supervisor confirma el pago: las cuotas liquidadas quedan PAGADAS (con el monto realmente pagado), la
     * siguiente no pagada pasa a ACTIVA y se recalcula el saldo. Sin cuotas por pagar -> préstamo PAGADO con
     * [Loan.closedAt] (desde ahí corren las 48 h para volver a solicitar).
     * 404 inexistente · 409 ya no está PENDIENTE.
     */
    @Transactional
    fun confirm(paymentId: UUID, reviewedBy: String?): LoanPaymentResponse {
        val payment = pendingForUpdate(paymentId)
        val loan = loanRepository.findByIdForUpdate(payment.loanId) ?: throw ResourceNotFoundException("El préstamo no existe")
        val now = LoanPayment.now()

        val installments = loanInstallmentRepository.findAllByLoanIdOrderByInstallmentNumberAsc(loan.id!!)
        val byId = installments.associateBy { it.id }
        val items = loanPaymentItemRepository.findAllByPaymentId(paymentId)
        val changed = LinkedHashSet<LoanInstallment>()
        items.forEach { item ->
            val installment = byId[item.installmentId] ?: return@forEach
            if (installment.status == InstallmentStatus.PAGADA) return@forEach
            installment.paidAmount = item.amount
            installment.paidAt = now
            installment.status = InstallmentStatus.PAGADA
            installment.updatedAt = now
            changed += installment
        }
        // Primero se liberan las que dejan de estar ACTIVA (índice único de cuota ACTIVA por préstamo)
        loanInstallmentRepository.saveAllAndFlush(changed.toList())
        loanInstallmentRepository.saveAllAndFlush(LoanSchedules.normalizeStates(installments, now))

        loan.outstandingBalance = LoanSchedules.outstanding(installments)
        if (installments.all { it.status == InstallmentStatus.PAGADA }) {
            loan.status = LoanStatus.PAGADO
            loan.closedAt = now
        }
        loan.updatedAt = now
        loanRepository.save(loan)

        payment.status = PaymentStatus.CONFIRMADO
        payment.reviewedBy = reviewedBy
        payment.reviewedAt = now
        payment.updatedAt = now
        loanPaymentRepository.save(payment)
        log.info("Pago {} confirmado; préstamo {}: saldo {} ({})", paymentId, loan.id, loan.outstandingBalance, loan.status)
        return payment.toResponse()
    }

    /** El supervisor rechaza el pago (motivo opcional). El préstamo no cambia. 404 · 409 ya no está PENDIENTE. */
    @Transactional
    fun reject(paymentId: UUID, reason: String?, reviewedBy: String?): LoanPaymentResponse {
        val payment = pendingForUpdate(paymentId)
        val now = LoanPayment.now()
        payment.status = PaymentStatus.RECHAZADO
        payment.rejectionReason = reason?.trim()?.takeIf { it.isNotEmpty() }
        payment.reviewedBy = reviewedBy
        payment.reviewedAt = now
        payment.updatedAt = now
        loanPaymentRepository.save(payment)
        return payment.toResponse()
    }

    /** Pagos rechazados del usuario que aún no vio (notificación PAYMENT_REJECTED). */
    @Transactional(readOnly = true)
    fun unseenRejected(userId: UUID): List<LoanPaymentResponse> =
        loanPaymentRepository.findAllByUserIdAndStatusAndRejectionSeenAtIsNullOrderByReviewedAtDesc(userId, PaymentStatus.RECHAZADO)
            .map { it.toResponse() }

    /** El cliente vio el rechazo del pago [paymentId] (debe ser suyo y estar RECHAZADO; si no, 404). Idempotente. */
    @Transactional
    fun markRejectionSeen(userId: UUID, paymentId: UUID) {
        val payment = loanPaymentRepository.findById(paymentId).orElse(null)
            ?.takeIf { it.userId == userId && it.status == PaymentStatus.RECHAZADO }
            ?: throw ResourceNotFoundException("El pago rechazado no existe")
        if (payment.rejectionSeenAt == null) {
            payment.rejectionSeenAt = LoanPayment.now()
            loanPaymentRepository.save(payment)
        }
    }

    private fun pendingForUpdate(paymentId: UUID): LoanPayment {
        val payment = loanPaymentRepository.findByIdForUpdate(paymentId) ?: throw ResourceNotFoundException("El pago no existe")
        if (payment.status != PaymentStatus.PENDIENTE) {
            throw ConflictException("El pago no está PENDIENTE (estado actual: ${payment.status})")
        }
        return payment
    }

    private fun LoanPayment.toResponse(phone: String? = null): LoanPaymentResponse {
        val numbers = loanPaymentItemRepository.findAllByPaymentId(id!!).map { it.installmentNumber }.sorted()
        return LoanPaymentResponse(
            idPago = id!!,
            idPrestamo = loanId,
            tipo = type.name,
            monto = amount,
            numeroCuota = if (type == PaymentType.CUOTA) numbers.firstOrNull() else null,
            codigoOperacion = operationCode,
            estado = status.name,
            fechaRegistro = createdAt,
            numCelular = phone,
            motivoRechazo = rejectionReason,
            fechaPago = paymentDate,
            capital = principal,
            interes = interest,
            interesMoratorio = moratorium,
            comision = commission,
            apis = apis,
            cuotas = numbers.takeIf { it.isNotEmpty() }
        )
    }

    companion object {
        const val INVALID_STATUS = "estado debe ser PENDIENTE, CONFIRMADO o RECHAZADO"
    }
}
