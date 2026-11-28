package com.join8.identifyr.service.credit

import com.join8.identifyr.model.entity.InstallmentStatus
import com.join8.identifyr.model.entity.LoanStatus
import com.join8.identifyr.model.entity.PaymentStatus
import com.join8.identifyr.repository.LoanInstallmentRepository
import com.join8.identifyr.repository.LoanPaymentRepository
import com.join8.identifyr.repository.LoanRepository
import com.join8.identifyr.repository.LoanRequestRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * Al arrancar, recalcula con el motor de crédito los préstamos VIGENTES creados con la fórmula anterior
 * (engine_version nulo): mismo monto, modalidad y fecha de desembolso, parámetros vigentes a esa fecha.
 * Idempotente: los recalculados quedan con engine_version = [LoanSchedules.ENGINE_VERSION].
 *
 * No se recalcula (queda en log) un préstamo con cuotas ya pagadas o con un pago pendiente de confirmación:
 * mezclar montos de dos fórmulas daría un saldo incoherente; esos casos se revisan a mano.
 */
@Component
class LegacyLoanRecalculator(
    private val loanRepository: LoanRepository,
    private val loanInstallmentRepository: LoanInstallmentRepository,
    private val loanRequestRepository: LoanRequestRepository,
    private val loanPaymentRepository: LoanPaymentRepository,
    private val creditParameterService: CreditParameterService,
    private val transactionTemplate: TransactionTemplate
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments?) {
        recalculateAll()
    }

    /** Devuelve cuántos préstamos se recalcularon. */
    fun recalculateAll(): Int {
        val ids = loanRepository.findAllByStatusAndEngineVersionIsNull(LoanStatus.VIGENTE).mapNotNull { it.id }
        if (ids.isEmpty()) return 0
        var done = 0
        ids.forEach { id ->
            try {
                if (transactionTemplate.execute { recalculate(id) } == true) done++
            } catch (e: Exception) {
                log.error("No se pudo recalcular el préstamo {}", id, e)
            }
        }
        log.info("Préstamos recalculados con el motor de crédito: {} de {}", done, ids.size)
        return done
    }

    private fun recalculate(loanId: UUID): Boolean {
        val loan = loanRepository.findByIdForUpdate(loanId) ?: return false
        if (loan.engineVersion != null || loan.status != LoanStatus.VIGENTE) return false
        val installments = loanInstallmentRepository.findAllByLoanIdOrderByInstallmentNumberAsc(loanId)
        if (installments.any { it.status == InstallmentStatus.PAGADA } ||
            loanPaymentRepository.findFirstByLoanIdAndStatus(loanId, PaymentStatus.PENDIENTE) != null
        ) {
            log.warn("Préstamo {} sin recalcular: tiene cuotas pagadas o un pago pendiente", loanId)
            return false
        }

        val modality = Modality.forPeriod(loan.installmentFrequency)
            ?: Modality.forPeriod(loanRequestRepository.findById(loan.loanRequestId).orElse(null)?.installmentFrequency)
            ?: Modality.entries.firstOrNull { it.installments == loan.installmentCount }
            ?: Modality.QUINCENAL
        val params = creditParameterService.parametersAt(loan.disbursementDate)
        val sim = CreditEngine.simulate(loan.amount, modality, loan.disbursementDate, params, creditParameterService.holidays())
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val previousTotal = installments.fold(BigDecimal.ZERO) { a, i -> a.add(i.amount) }

        // Mismo número de cuotas: se actualizan en su lugar; si cambia (préstamos antiguos de una sola cuota) se rehacen
        if (installments.size == sim.schedule.size) {
            installments.sortedBy { it.installmentNumber }.zip(sim.schedule).forEach { (installment, row) ->
                LoanSchedules.copyRow(installment, row)
                installment.status = InstallmentStatus.PENDIENTE
                installment.updatedAt = now
            }
            loanInstallmentRepository.saveAllAndFlush(installments)
            loanInstallmentRepository.saveAllAndFlush(LoanSchedules.normalizeStates(installments, now))
        } else {
            loanInstallmentRepository.deleteAll(installments)
            loanInstallmentRepository.flush()
            loanInstallmentRepository.saveAllAndFlush(LoanSchedules.newInstallments(loanId, sim, now))
        }

        LoanSchedules.applySnapshot(loan, sim, params)
        loan.outstandingBalance = sim.totalToPay
        loan.updatedAt = now
        loanRepository.save(loan)
        log.info("Préstamo {} recalculado: {} x{} de S/ {} (antes total S/ {})", loanId, modality, modality.installments,
            sim.installmentAmount, previousTotal)
        return true
    }
}
