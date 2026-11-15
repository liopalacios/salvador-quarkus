package com.join8.identifyr.service

/**
 * Planes de préstamo: ÚNICA fuente de periodos, cuotas y tasas del back.
 * Deben coincidir exactamente con la oferta que ve el usuario en la app.
 * Tasas en puntos básicos (18% = 1800) para calcular en enteros.
 */
enum class LoanPlan(
    val frequencyDays: Int,
    val installmentCount: Int,
    val interestRateBp: Int,
    /** Solo referencia (pago anticipado); no interviene en el cronograma. */
    val earlyPaymentRateBp: Int
) {
    QUINCENAL(frequencyDays = 15, installmentCount = 4, interestRateBp = 1800, earlyPaymentRateBp = 1500),
    MENSUAL(frequencyDays = 30, installmentCount = 2, interestRateBp = 2100, earlyPaymentRateBp = 1800);

    companion object {
        /** Plazo total fijo: Google Play no permite exigir el pago total en 60 días o menos. */
        const val TERM_DAYS = 61

        fun forFrequency(frequencyDays: Int?): LoanPlan? = entries.firstOrNull { it.frequencyDays == frequencyDays }

        val allowedFrequencies: List<Int> get() = entries.map { it.frequencyDays }
    }
}
