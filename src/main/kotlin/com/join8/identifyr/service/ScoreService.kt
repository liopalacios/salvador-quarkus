package com.join8.identifyr.service

import com.join8.identifyr.model.dto.request.QuestionnaireRequest
import com.join8.identifyr.model.dto.response.ScoreResponse
import com.join8.identifyr.model.entity.Questionnaire
import org.springframework.stereotype.Service

/**
 * Única fuente de verdad nivel -> montos. levelName y maxLoanAmount salen siempre de la misma entrada.
 * Los cortes se aplican sobre el puntaje redondeado (el mismo scoreValue que se devuelve):
 * BRONCE <= 25 < PLATA <= 50 < ORO, es decir 25.5 ya es PLATA y 50.5 ya es ORO.
 */
enum class ScoreLevel(val levelName: String, val minLoanAmount: Int, val maxLoanAmount: Int) {
    BRONZE("BRONCE", 50, 50),
    SILVER("PLATA", 50, 300),
    GOLD("ORO", 50, 450);

    companion object {
        fun forScore(score: Int): ScoreLevel = when {
            score <= 25 -> BRONZE
            score <= 50 -> SILVER
            else -> GOLD
        }
    }
}

/**
 * Bloque 2 ("Completar perfil"): cada campo suma una sola vez si tiene valor (no null y no vacío),
 * sin importar su contenido. Los pesos (total 58) se escalan al 30%: cada punto vale 0.3,
 * así que el bloque completo aporta 58 * 0.3 = 17.4 al score (máximo total 67.9 + 17.4 = 85.3).
 */
enum class ProfileField(val weight: Int, private val label: String, val valueOf: (Questionnaire) -> String?) {
    EMAIL(5, "tu correo electrónico", Questionnaire::email),
    ADDRESS(8, "tu dirección exacta", Questionnaire::address),
    RUC(6, "tu RUC", Questionnaire::ruc),
    DRIVER_LICENSE(6, "tu licencia de conducir", Questionnaire::driverLicense),
    VEHICLE_PLATE(5, "la placa de tu vehículo", Questionnaire::vehiclePlate),
    EDUCATION_LEVEL(4, "tu nivel de estudios", Questionnaire::educationLevel),
    WORKPLACE(6, "tu lugar de trabajo", Questionnaire::workplace),
    WORK_APPS(10, "tus apps de trabajo", Questionnaire::workApps),
    ACCOUNT_STATEMENT(8, "tu estado de cuenta", Questionnaire::accountStatementPhotoId);

    /** Aporte real al score, en milésimas: peso * 0.3 (5 -> 1500 = 1.5 puntos). */
    val thousandths: Int get() = weight * BLOCK2_FACTOR_THOUSANDTHS

    /**
     * Motivo con el peso que muestra la pantalla "Completar perfil" de la app, ej. "Agregaste tu correo electrónico (+5)".
     * Es el peso del formulario, no el aporte al score (que es peso * 0.3).
     */
    val reason: String get() = "Agregaste $label (+$weight)"

    fun isFilled(q: Questionnaire): Boolean = !valueOf(q).isNullOrBlank()

    companion object {
        /** El bloque 2 pesa 30%: cada punto de peso vale 0.3 (300 milésimas). */
        const val BLOCK2_FACTOR_THOUSANDTHS = 300
    }
}

@Service
class ScoreService {

    companion object {
        // Pesos expresados en milésimas (0.21 -> 210) para sumar con enteros y evitar errores de punto flotante
        private const val WEIGHT_Q1 = 210
        private const val WEIGHT_Q2 = 140
        private const val WEIGHT_Q3 = 105
        private const val WEIGHT_Q4 = 105
        private const val WEIGHT_Q5 = 140

        // Q1 employmentType: ¿A qué te dedicas?
        private val Q1_POINTS = mapOf(
            "1a" to 90, // Bodega o negocio propio
            "1b" to 80, // Servicios profesionales (estilista, costura, limpieza)
            "1c" to 72, // Comercio y ventas
            "1d" to 65, // Profesional independiente (cualquier actividad)
            "1e" to 58, // Comida o gastronomía (menú, carretilla, mozo)
            "1f" to 50, // Taxi colectivo
            "1g" to 42, // Construcción
            "1h" to 35  // Delivery y reparto
        )
        // Q2 employmentTenure: 2a < 6 meses, 2b 6-12 meses, 2c 1-3 años, 2d > 3 años
        private val Q2_POINTS = mapOf("2a" to 0, "2b" to 35, "2c" to 70, "2d" to 100)
        // Q3 residentialTenure: 3a < 6 meses, 3b 6-12 meses, 3c 1-3 años, 3d > 3 años
        private val Q3_POINTS = mapOf("3a" to 0, "3b" to 35, "3c" to 70, "3d" to 100)
        // Q4 housingType: 4a Propia, 4b Alquilada, 4c Familiar, 4d Cedida
        private val Q4_POINTS = mapOf("4a" to 100, "4b" to 85, "4c" to 55, "4d" to 25)
        // Q5 dependents: 5a 0, 5b 1, 5c 2, 5d 3 o más
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

        private val TABLES = mapOf(
            "employmentType" to Q1_POINTS,
            "employmentTenure" to Q2_POINTS,
            "residentialTenure" to Q3_POINTS,
            "housingType" to Q4_POINTS,
            "dependents" to Q5_POINTS
        )
    }

    fun calculate(request: QuestionnaireRequest): ScoreResponse {
        val base = calculateBase(
            request.employmentType, request.employmentTenure, request.residentialTenure,
            request.housingType, request.dependents
        )
        return toResponse(base.thousandths, base.reasons)
    }

    /**
     * Score de un registro de cuestionario: bloque 1 (ponderado) + bloque 2 (pesos * 0.3).
     * Se suman los valores exactos y se redondea una sola vez; nivel y monto salen del score final.
     */
    fun calculate(questionnaire: Questionnaire): ScoreResponse {
        fun required(value: String?, field: String) =
            value ?: throw IllegalStateException("El cuestionario ${questionnaire.id} no tiene la respuesta $field")

        val base = calculateBase(
            required(questionnaire.employmentType, "employmentType"),
            required(questionnaire.employmentTenure, "employmentTenure"),
            required(questionnaire.residentialTenure, "residentialTenure"),
            required(questionnaire.housingType, "housingType"),
            required(questionnaire.dependents, "dependents")
        )
        val filled = ProfileField.entries.filter { it.isFilled(questionnaire) }
        return toResponse(
            base.thousandths + filled.sumOf { it.thousandths },
            base.reasons + filled.map { it.reason }
        )
    }

    private fun toResponse(scoreThousandths: Int, reasons: List<String>): ScoreResponse {
        val score = roundScore(scoreThousandths)
        val level = ScoreLevel.forScore(score)
        return ScoreResponse(
            scoreValue = score,
            levelName = level.levelName,
            maxLoanAmount = level.maxLoanAmount,
            reasons = reasons
        )
    }

    private class BaseScore(val thousandths: Int, val reasons: List<String>)

    /** Normaliza un código del bloque 1 y verifica que sea válido para su campo; lanza IllegalArgumentException si no. */
    fun normalizeCode(field: String, code: String): String {
        val table = TABLES[field] ?: throw IllegalArgumentException("Campo desconocido: $field")
        return code.normalized().also { pointsFor(table, it, field) }
    }

    private fun calculateBase(
        employmentType: String, employmentTenure: String, residentialTenure: String,
        housingType: String, dependents: String
    ): BaseScore {
        val q1 = employmentType.normalized()
        val q2 = employmentTenure.normalized()
        val q3 = residentialTenure.normalized()
        val q4 = housingType.normalized()
        val q5 = dependents.normalized()

        val weightedSum = pointsFor(Q1_POINTS, q1, "employmentType") * WEIGHT_Q1 +
                pointsFor(Q2_POINTS, q2, "employmentTenure") * WEIGHT_Q2 +
                pointsFor(Q3_POINTS, q3, "residentialTenure") * WEIGHT_Q3 +
                pointsFor(Q4_POINTS, q4, "housingType") * WEIGHT_Q4 +
                pointsFor(Q5_POINTS, q5, "dependents") * WEIGHT_Q5

        return BaseScore(weightedSum, buildReasons(listOf(q1, q2, q3, q4, q5)))
    }

    // Milésimas -> entero más cercano, mitades hacia arriba: 25.499 -> 25, 25.5 -> 26, 50.5 -> 51
    internal fun roundScore(scoreThousandths: Int): Int = (scoreThousandths + 500) / 1000

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
