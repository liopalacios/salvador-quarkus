package com.join8.identifyr.service.credit

import com.join8.identifyr.repository.CreditParameterRepository
import com.join8.identifyr.repository.HolidayRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/** Parámetros del motor vigentes a una fecha y tabla de feriados (tablas credit_parameter y holiday). */
@Service
class CreditParameterService(
    private val parameterRepository: CreditParameterRepository,
    private val holidayRepository: HolidayRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Valor vigente de cada código a [date]. Si ninguno está vigente (p. ej. préstamo anterior al primer valor
     * cargado) se usa el más cercano: el último que empezó antes o, si no hay, el primero que empieza después.
     * Sin ningún valor para un código -> IllegalStateException (la tabla debe estar cargada).
     */
    @Transactional(readOnly = true)
    fun parametersAt(date: LocalDate): CreditParameters = CreditParameters(
        teaCap = valueAt(TEA_TOPE, date),
        moraFactor = valueAt(TEA_MORA_FACTOR, date),
        commissionPct = valueAt(COMISION_PCT, date),
        apisAmount = valueAt(APIS_MONTO, date),
        baseDays = valueAt(BASE_DIAS, date).toInt()
    )

    @Transactional(readOnly = true)
    fun holidays(): Set<LocalDate> = holidayRepository.findAll().map { it.date }.toSet()

    private fun valueAt(code: String, date: LocalDate) =
        parameterRepository.findAllByCode(code).let { rows ->
            rows.filter { !it.validFrom.isAfter(date) && (it.validTo?.isBefore(date) != true) }
                .maxByOrNull { it.validFrom }
                ?: rows.filter { !it.validFrom.isAfter(date) }.maxByOrNull { it.validFrom }
                    ?.also { log.warn("Parámetro {} sin valor vigente al {}: se usa el de {}", code, date, it.validFrom) }
                ?: rows.minByOrNull { it.validFrom }
                    ?.also { log.warn("Parámetro {} sin valor al {}: se usa el primero ({})", code, date, it.validFrom) }
                ?: throw IllegalStateException("Falta el parámetro de crédito $code")
        }.value

    companion object {
        const val TEA_TOPE = "TEA_TOPE"
        const val TEA_MORA_FACTOR = "TEA_MORA_FACTOR"
        const val COMISION_PCT = "COMISION_PCT"
        const val APIS_MONTO = "APIS_MONTO"
        const val BASE_DIAS = "BASE_DIAS"
    }
}
