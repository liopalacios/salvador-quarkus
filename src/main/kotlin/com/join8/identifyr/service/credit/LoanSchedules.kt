package com.join8.identifyr.service.credit

import com.join8.identifyr.model.entity.InstallmentStatus
import com.join8.identifyr.model.entity.Loan
import com.join8.identifyr.model.entity.LoanInstallment
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.*

/** Conversión entre el motor ([CreditEngine]) y las entidades del préstamo. */
object LoanSchedules {

    const val ENGINE_VERSION = 3

    /** Copia en el préstamo las condiciones y el snapshot de parámetros de la simulación. */
    fun applySnapshot(loan: Loan, sim: CreditSimulation, params: CreditParameters) {
        loan.modality = sim.modality
        loan.tea = sim.tea.setScale(6, RoundingMode.HALF_UP)
        loan.ted = sim.ted.setScale(25, RoundingMode.HALF_UP)
        loan.tedMora = sim.tedMora.setScale(25, RoundingMode.HALF_UP)
        loan.commissionPct = params.commissionPct.setScale(4, RoundingMode.HALF_UP)
        loan.apisAmount = params.apisAmount.setScale(2, RoundingMode.HALF_UP)
        loan.commissionTotal = sim.commissionTotal
        loan.apisTotal = sim.apisTotal
        loan.installmentAmount = sim.installmentAmount
        loan.tcea = sim.tcea.setScale(10, RoundingMode.HALF_UP)
        loan.engineVersion = ENGINE_VERSION
        loan.interest = sim.interestTotal
        loan.interestRate = sim.tea.setScale(4, RoundingMode.HALF_UP)
        loan.totalAmount = sim.totalToPay
        loan.installmentFrequency = sim.modality.periodDays
        loan.installmentCount = sim.modality.installments
        loan.termDays = sim.termDays
        loan.paymentDate = sim.finalDueDate
        loan.appPaymentDate = sim.finalDueDate
    }

    /** Cuotas nuevas del cronograma: la 1 queda ACTIVA y el resto PENDIENTE. */
    fun newInstallments(loanId: UUID, sim: CreditSimulation, now: Instant): List<LoanInstallment> =
        sim.schedule.map { row ->
            LoanInstallment(
                loanId = loanId, installmentNumber = row.number, dueDate = row.dueDate, amount = row.installment,
                status = if (row.number == 1) InstallmentStatus.ACTIVA else InstallmentStatus.PENDIENTE,
                createdAt = now
            ).also { copyRow(it, row) }
        }

    /** Escribe en la cuota el desglose de la fila del cronograma (para recalcular una cuota existente). */
    fun copyRow(installment: LoanInstallment, row: ScheduleRow) {
        installment.dueDate = row.dueDate
        installment.amount = row.installment
        installment.tDays = row.tDays
        installment.tPrevDays = row.tPrevDays
        installment.openingBalance = row.openingBalance
        installment.interest = row.interest
        installment.principal = row.principal
        installment.commission = row.commission
        installment.apis = row.apis
        installment.closingBalance = row.closingBalance
    }

    /** Fila del motor a partir de la cuota guardada; null si la cuota no tiene desglose (préstamo sin recalcular). */
    fun toRow(i: LoanInstallment): ScheduleRow? {
        return ScheduleRow(
            number = i.installmentNumber,
            nominalDate = i.dueDate,
            dueDate = i.dueDate,
            tDays = i.tDays ?: return null,
            tPrevDays = i.tPrevDays ?: return null,
            openingBalance = i.openingBalance ?: return null,
            interest = i.interest ?: return null,
            principal = i.principal ?: return null,
            commission = i.commission ?: return null,
            apis = i.apis ?: return null,
            installment = i.amount,
            closingBalance = i.closingBalance ?: return null
        )
    }

    /** Cuota ACTIVA liquidada a [paymentDate]; null si no hay cuota por pagar o el préstamo no tiene snapshot del motor. */
    fun quoteActive(loan: Loan, installments: List<LoanInstallment>, paymentDate: java.time.LocalDate): Pair<LoanInstallment, InstallmentQuote>? {
        val ted = loan.ted ?: return null
        val tedMora = loan.tedMora ?: return null
        val active = installments.filter { it.status != InstallmentStatus.PAGADA }.minByOrNull { it.installmentNumber } ?: return null
        val row = toRow(active) ?: return null
        return active to CreditEngine.quoteInstallment(row, loan.disbursementDate, paymentDate, ted, tedMora)
    }

    /** F28: cancelar todas las cuotas no pagadas a [paymentDate]; null si no hay o falta el snapshot. */
    fun quotePayoff(loan: Loan, installments: List<LoanInstallment>, paymentDate: java.time.LocalDate): Pair<List<LoanInstallment>, PayoffQuote>? {
        val ted = loan.ted ?: return null
        val tedMora = loan.tedMora ?: return null
        val unpaid = installments.filter { it.status != InstallmentStatus.PAGADA }.sortedBy { it.installmentNumber }
        if (unpaid.isEmpty()) return null
        val rows = unpaid.map { toRow(it) ?: return null }
        return unpaid to CreditEngine.quotePayoff(rows, loan.disbursementDate, paymentDate, ted, tedMora)
    }

    /** Saldo pendiente = cuotas del contrato que faltan pagar. */
    fun outstanding(installments: List<LoanInstallment>): BigDecimal =
        installments.filter { it.status != InstallmentStatus.PAGADA }
            .fold(BigDecimal.ZERO.setScale(2)) { acc, i -> acc.add(i.amount) }

    /** Deja ACTIVA solo la primera cuota no pagada; las demás no pagadas quedan PENDIENTE. Devuelve las que cambiaron. */
    fun normalizeStates(installments: List<LoanInstallment>, now: Instant): List<LoanInstallment> {
        val firstUnpaid = installments.filter { it.status != InstallmentStatus.PAGADA }.minByOrNull { it.installmentNumber }
        return installments.filter { it.status != InstallmentStatus.PAGADA }.mapNotNull { i ->
            val wanted = if (i === firstUnpaid) InstallmentStatus.ACTIVA else InstallmentStatus.PENDIENTE
            if (i.status == wanted) null else i.also { it.status = wanted; it.updatedAt = now }
        }
    }
}
