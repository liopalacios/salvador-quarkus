package com.join8.identifyr.service

import com.join8.identifyr.config.LoanProperties
import com.join8.identifyr.exception.BusinessException
import com.join8.identifyr.exception.ConflictException
import com.join8.identifyr.model.dto.request.LoanRegisterRequest
import com.join8.identifyr.model.dto.response.LoanRegisterResponse
import com.join8.identifyr.model.entity.Loan
import com.join8.identifyr.model.entity.LoanRequest
import com.join8.identifyr.model.entity.LoanRequestStatus
import com.join8.identifyr.repository.LoanRepository
import com.join8.identifyr.repository.LoanRequestRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*

/** Condiciones calculadas en el servidor para un monto y un día de registro. */
data class LoanTerms(
    val interest: Int,
    val totalAmount: Int,
    val paymentDate: LocalDate,
    val appPaymentDate: LocalDate
)

@Service
class LoanService(
    private val loanRepository: LoanRepository,
    private val loanRequestRepository: LoanRequestRepository,
    private val props: LoanProperties,
    private val clock: Clock
) {

    companion object {
        const val MAX_OPERATION_CODE_LENGTH = 50
    }

    /** interés = floor(monto * tasa); fechas = día de registro + plazos configurados. */
    fun terms(amount: Int, registrationDay: LocalDate): LoanTerms {
        val interest = amount.toBigDecimal().multiply(props.interestRate).setScale(0, RoundingMode.FLOOR).intValueExact()
        return LoanTerms(
            interest = interest,
            totalAmount = amount + interest,
            paymentDate = registrationDay.plusDays(props.paymentTermDays),
            appPaymentDate = registrationDay.plusDays(props.appPaymentTermDays)
        )
    }

    /**
     * Registra el préstamo de una solicitud PENDIENTE y, en la misma transacción, pasa la solicitud a
     * PROCESADA con su código de operación. Si algo falla, no se guarda ninguno de los dos.
     *
     * Orden de los chequeos: la solicitud se bloquea (FOR UPDATE) y se mira primero si ya tiene préstamo,
     * para que un reintento de la app (aunque sea al día siguiente) reciba 409 con el id existente y no
     * un 400 por fechas o un 422 por estado. Nunca 404: la app lo trata como "endpoint no publicado".
     */
    @Transactional
    fun register(request: LoanRegisterRequest, createdBy: String?): LoanRegisterResponse {
        val requestId = UUID.fromString(request.idSolicitud!!)
        val operationCode = request.codigoOperacion!!.trim()
        require(operationCode.length <= MAX_OPERATION_CODE_LENGTH) {
            "codigoOperacion: no puede tener más de $MAX_OPERATION_CODE_LENGTH caracteres"
        }

        val loanRequest = loanRequestRepository.findByIdForUpdate(requestId)
            ?: throw BusinessException("La solicitud $requestId no existe")

        loanRepository.findByLoanRequestId(requestId)?.let {
            throw ConflictException("La solicitud ya tiene un préstamo registrado", idPrestamo = it.id)
        }
        if (loanRequest.status != LoanRequestStatus.PENDIENTE) {
            throw BusinessException("La solicitud no está PENDIENTE (estado actual: ${loanRequest.status})")
        }

        val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
        val terms = terms(loanRequest.requestedAmount, LocalDate.ofInstant(now, props.zone))
        validateAgainst(request, loanRequest, terms)

        if (loanRepository.existsByOperationCode(operationCode)) throw duplicatedOperationCode(operationCode)

        val loan = try {
            loanRepository.saveAndFlush(newLoan(loanRequest, operationCode, terms, createdBy, now))
        } catch (e: DataIntegrityViolationException) {
            // Carrera con otro préstamo que usó el mismo código entre el exists y el insert.
            // (Una carrera sobre la misma solicitud no llega aquí: la serializa el FOR UPDATE.)
            throw duplicatedOperationCode(operationCode)
        }

        loanRequest.status = LoanRequestStatus.PROCESADA
        loanRequest.operationCode = operationCode
        loanRequest.updatedAt = now
        loanRequestRepository.saveAndFlush(loanRequest)

        return LoanRegisterResponse(
            id = loan.id!!,
            idSolicitud = loan.loanRequestId,
            monto = loan.amount,
            interes = loan.interest,
            totalAPagar = loan.totalAmount,
            codigoOperacion = loan.operationCode,
            fechaPago = loan.paymentDate,
            fechaPagoApp = loan.appPaymentDate,
            estado = loan.status.name,
            fechaRegistro = loan.createdAt
        )
    }

    /** La app manda lo que muestra; el servidor recalcula y rechaza cualquier diferencia, campo por campo. */
    private fun validateAgainst(request: LoanRegisterRequest, loanRequest: LoanRequest, terms: LoanTerms) {
        val errors = buildList {
            if (request.monto != loanRequest.requestedAmount)
                add("monto: debe ser igual al monto de la solicitud (${loanRequest.requestedAmount})")
            if (request.puntaje != loanRequest.score)
                add("puntaje: debe coincidir con el de la solicitud (${loanRequest.score})")
            if (request.categoria != loanRequest.category.name)
                add("categoria: debe coincidir con la de la solicitud (${loanRequest.category.name})")
            if (request.interes != terms.interest)
                add("interes: valor esperado ${terms.interest}")
            if (request.fechaPago != terms.paymentDate)
                add("fechaPago: valor esperado ${terms.paymentDate}")
            if (request.fechaPagoApp != terms.appPaymentDate)
                add("fechaPagoApp: valor esperado ${terms.appPaymentDate}")
        }
        require(errors.isEmpty()) { errors.joinToString(", ") }
    }

    private fun newLoan(
        loanRequest: LoanRequest, operationCode: String, terms: LoanTerms, createdBy: String?, now: Instant
    ) = Loan(
        loanRequestId = loanRequest.id!!,
        userId = loanRequest.userId,
        amount = loanRequest.requestedAmount,
        interest = terms.interest,
        interestRate = props.interestRate,
        totalAmount = terms.totalAmount,
        operationCode = operationCode,
        score = loanRequest.score,
        category = loanRequest.category,
        depositType = loanRequest.depositType,
        paymentDate = terms.paymentDate,
        appPaymentDate = terms.appPaymentDate,
        createdBy = createdBy,
        createdAt = now
    )

    private fun duplicatedOperationCode(code: String) =
        ConflictException("El codigoOperacion '$code' ya se usó en otro préstamo")
}
