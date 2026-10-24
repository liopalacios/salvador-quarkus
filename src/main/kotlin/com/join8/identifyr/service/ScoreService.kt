package com.join8.identifyr.service

import com.join8.identifyr.model.dto.request.QuestionnaireRequest
import com.join8.identifyr.model.dto.response.ScoreResponse
import org.springframework.stereotype.Service
import kotlin.math.roundToInt

@Service
class ScoreService {

    companion object {
        // Pesos expresados en milésimas (0.21 -> 210) para sumar con enteros y evitar errores de punto flotante
        private const val WEIGHT_Q1 = 210
        private const val WEIGHT_Q2 = 140
        private const val WEIGHT_Q3 = 105
        private const val WEIGHT_Q4 = 105
        private const val WEIGHT_Q5 = 140

        private val Q1_POINTS = mapOf(
            "1a" to 90, "1b" to 80, "1c" to 72, "1d" to 65,
            "1e" to 58, "1f" to 50, "1g" to 42, "1h" to 35
        )
        private val Q2_POINTS = mapOf("2a" to 0, "2b" to 35, "2c" to 70, "2d" to 100)
        private val Q3_POINTS = mapOf("3a" to 0, "3b" to 35, "3c" to 70, "3d" to 100)
        private val Q4_POINTS = mapOf("4a" to 100, "4b" to 85, "4c" to 55, "4d" to 25)
        private val Q5_POINTS = mapOf("5a" to 100, "5b" to 70, "5c" to 40, "5d" to 15)

        private val REASONS = mapOf(
            "1c" to "Tu actividad como comercio y ventas sumó puntos",
            "2c" to "Tu antigüedad en tu actividad actual suma estabilidad",
            "2d" to "Llevas tiempo en lo mismo, eso genera confianza",
            "3c" to "Vivir varios años en el mismo lugar muestra estabilidad",
            "3d" to "Vivir mucho tiempo en el mismo lugar muestra estabilidad",
            "4a" to "Tu situación de vivienda fortalece tu perfil",
            "4b" to "Tu situación de vivienda suma puntos a tu perfil",
            "5a" to "Tu carga familiar favorece tu capacidad de pago",
            "5b" to "Tu carga familiar es manejable para tu capacidad de pago"
        )
        private const val GENERIC_EMPLOYMENT_REASON = "Tu actividad económica sumó puntos"
        private const val COMPLETE_PROFILE_REASON = "Completaste tu perfil sin dejar campos vacíos"

        const val LEVEL_BRONZE = "BRONCE"
        const val LEVEL_SILVER = "PLATA"
        const val LEVEL_GOLD = "ORO"
    }

    fun calculate(request: QuestionnaireRequest): ScoreResponse {
        val q1 = request.employmentType.normalized()
        val q2 = request.employmentTenure.normalized()
        val q3 = request.residentialTenure.normalized()
        val q4 = request.housingType.normalized()
        val q5 = request.dependents.normalized()

        val weightedSum = pointsFor(Q1_POINTS, q1, "employmentType") * WEIGHT_Q1 +
                pointsFor(Q2_POINTS, q2, "employmentTenure") * WEIGHT_Q2 +
                pointsFor(Q3_POINTS, q3, "residentialTenure") * WEIGHT_Q3 +
                pointsFor(Q4_POINTS, q4, "housingType") * WEIGHT_Q4 +
                pointsFor(Q5_POINTS, q5, "dependents") * WEIGHT_Q5

        // Redondeo al entero más cercano (mitades hacia arriba)
        val score = (weightedSum + 500) / 1000
        val (level, amount) = levelAndAmount(score)

        return ScoreResponse(
            scoreValue = score,
            levelName = level,
            maxLoanAmount = amount,
            reasons = buildReasons(listOf(q1, q2, q3, q4, q5))
        )
    }

    internal fun levelAndAmount(score: Int): Pair<String, Int> = when {
        score <= 25 -> LEVEL_BRONZE to 50
        score <= 50 -> LEVEL_SILVER to roundToNearest50(50 + (score - 26) / 24.0 * 250).coerceAtMost(300)
        else -> LEVEL_GOLD to roundToNearest50(50 + (score - 51) / 19.0 * 450).coerceAtMost(500)
    }

    private fun roundToNearest50(amount: Double): Int = (amount / 50).roundToInt() * 50

    private fun buildReasons(codes: List<String>): List<String> {
        val reasons = mutableListOf<String>()
        val employment = codes.first()
        val employmentReason = REASONS[employment]
            ?: GENERIC_EMPLOYMENT_REASON.takeIf { Q1_POINTS.getValue(employment) >= 58 }
        employmentReason?.let { reasons.add(it) }
        codes.drop(1).mapNotNullTo(reasons) { REASONS[it] }
        reasons.add(COMPLETE_PROFILE_REASON)
        return reasons
    }

    private fun pointsFor(table: Map<String, Int>, code: String, field: String): Int =
        table[code] ?: throw IllegalArgumentException(
            "Código inválido '$code' para $field. Valores permitidos: ${table.keys.joinToString()}"
        )

    private fun String.normalized() = trim().lowercase()
}
