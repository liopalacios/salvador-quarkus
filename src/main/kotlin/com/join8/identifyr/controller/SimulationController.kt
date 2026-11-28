package com.join8.identifyr.controller

import com.join8.identifyr.config.LoanProperties
import com.join8.identifyr.service.credit.CreditEngine
import com.join8.identifyr.service.credit.CreditParameterService
import com.join8.identifyr.service.credit.CreditResponses
import com.join8.identifyr.service.credit.Modality
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate

/** POST /simulaciones: cuota, cronograma, total y TCEA de un crédito que se desembolsara hoy (F01–F20). */
data class SimulationRequest(
    @field:NotNull(message = "es obligatorio")
    @field:Min(value = 1, message = "debe ser mayor que 0")
    @field:Max(value = 100_000, message = "no puede superar 100000")
    val monto: Int? = null,

    /** Días entre cuotas: 15 (quincenal) o 30 (mensual). */
    @field:NotNull(message = "es obligatorio")
    val frecuenciaCuota: Int? = null
)

data class SimulationResponse(
    val monto: BigDecimal,
    /** QUINCENAL | MENSUAL */
    val modalidad: String,
    val frecuenciaCuota: Int,
    val numeroCuotas: Int,
    /** Fecha de desembolso supuesta (hoy en Lima). */
    val fechaDesembolso: LocalDate,
    /** TEA y TCEA en porcentaje (114.68 = 114.68%). */
    val tea: BigDecimal,
    val tcea: BigDecimal,
    val cuota: BigDecimal,
    val comisionTotal: BigDecimal,
    val apisTotal: BigDecimal,
    val interesTotal: BigDecimal,
    val totalAPagar: BigDecimal,
    val plazoDias: Int,
    val cronograma: List<SimulationRow>
)

data class SimulationRow(
    val numeroCuota: Int,
    val fechaVencimiento: LocalDate,
    val cuota: BigDecimal,
    val capital: BigDecimal,
    val interes: BigDecimal,
    val comision: BigDecimal,
    val apis: BigDecimal,
    val saldoFinal: BigDecimal
)

@RestController
class SimulationController(
    private val creditParameterService: CreditParameterService,
    private val props: LoanProperties,
    private val clock: Clock
) {
    // Cliente autenticado. 200 · 400 monto o frecuencia inválidos
    @PostMapping("/simulaciones")
    fun simulate(@Valid @RequestBody request: SimulationRequest): ResponseEntity<SimulationResponse> {
        val modality = Modality.forPeriod(request.frecuenciaCuota)
            ?: throw IllegalArgumentException("frecuenciaCuota: solo se permite ${Modality.allowedPeriods.joinToString(" o ")}")
        val today = LocalDate.ofInstant(clock.instant(), props.zone)
        val sim = CreditEngine.simulate(
            BigDecimal(request.monto!!), modality, today,
            creditParameterService.parametersAt(today), creditParameterService.holidays()
        )
        return ResponseEntity.ok(
            SimulationResponse(
                monto = sim.amount,
                modalidad = modality.name,
                frecuenciaCuota = modality.periodDays,
                numeroCuotas = modality.installments,
                fechaDesembolso = today,
                tea = CreditResponses.percent(sim.tea),
                tcea = CreditResponses.percent(sim.tcea),
                cuota = sim.installmentAmount,
                comisionTotal = sim.commissionTotal,
                apisTotal = sim.apisTotal,
                interesTotal = sim.interestTotal,
                totalAPagar = sim.totalToPay,
                plazoDias = sim.termDays,
                cronograma = sim.schedule.map {
                    SimulationRow(it.number, it.dueDate, it.installment, it.principal, it.interest, it.commission, it.apis, it.closingBalance)
                }
            )
        )
    }
}
