package com.join8.identifyr.model.dto.response

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*

enum class CurrentLoanType { PRESTAMO_ACTIVO, SOLICITUD, PRESTAMO_PAGADO }

/**
 * GET /loans/current/phone/{phone}. Los campos que no aplican al [tipo] son null y se omiten del JSON.
 * - PRESTAMO_ACTIVO: datos del préstamo, la cuota ACTIVA, el cronograma completo ([cuotas]) y cuánto paga hoy
 *   ([cotizacionHoy]: la cuota ACTIVA y cancelar el 100%).
 * - SOLICITUD: solicitud sin préstamo. [estado] PENDIENTE (en evaluación) o RECHAZADA (la última solicitud
 *   fue rechazada; trae [motivoRechazo] si el supervisor lo escribió).
 * - PRESTAMO_PAGADO: el último préstamo ya se canceló; [nuevaSolicitudDesde] = cancelación + 48 h.
 */
data class CurrentLoanResponse(
    val tipo: CurrentLoanType,
    val idSolicitud: UUID,
    val estado: String,
    val carteraDigital: String,
    val montoSolicitado: Int? = null,
    val frecuenciaCuota: Int? = null,

    val idPrestamo: UUID? = null,
    val codigoOperacion: String? = null,
    val fechaDesembolso: LocalDate? = null,
    val montoDesembolsado: BigDecimal? = null,
    val montoTotal: BigDecimal? = null,
    val saldoPendiente: BigDecimal? = null,
    /** Cuota ACTIVA */
    val montoCuota: BigDecimal? = null,
    val numeroCuota: Int? = null,
    val totalCuotas: Int? = null,
    val fechaVencimiento: LocalDate? = null,
    val estadoCuota: String? = null,
    val motivoRechazo: String? = null,
    /** Solo en PRESTAMO_ACTIVO: todas las cuotas, de la primera a la última. */
    val cuotas: List<LoanInstallmentResponse>? = null,
    /** Solo en PRESTAMO_ACTIVO: pago informado que espera la confirmación del supervisor. */
    val pagoPendiente: LoanPaymentResponse? = null,
    /** Solo en PRESTAMO_ACTIVO: monto de la cuota ACTIVA y de cancelar el 100% si se paga hoy. */
    val cotizacionHoy: PaymentQuoteResponse? = null,
    /** TEA y TCEA en porcentaje (114.68 = 114.68%). */
    val tea: BigDecimal? = null,
    val tcea: BigDecimal? = null,
    /** Solo en PRESTAMO_PAGADO */
    val fechaCancelacion: Instant? = null,
    val nuevaSolicitudDesde: Instant? = null
)
