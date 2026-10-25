package com.join8.identifyr.service

import com.join8.identifyr.model.dto.request.QuestionnaireRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScoreServiceTest {

    private val service = ScoreService()

    private fun request(q1: String, q2: String, q3: String, q4: String, q5: String) =
        QuestionnaireRequest("user-1", q1, q2, q3, q4, q5)

    @Test
    fun `mejor perfil posible da ORO`() {
        // 90*.21 + 100*.14 + 100*.105 + 100*.105 + 100*.14 = 67.9 -> 68
        val response = service.calculate(request("1a", "2d", "3d", "4a", "5a"))
        assertEquals(68, response.scoreValue)
        assertEquals("ORO", response.levelName)
        assertEquals(450, response.maxLoanAmount)
    }

    @Test
    fun `peor perfil posible da BRONCE`() {
        // 35*.21 + 0 + 0 + 25*.105 + 15*.14 = 12.075 -> 12
        val response = service.calculate(request("1h", "2a", "3a", "4d", "5d"))
        assertEquals(12, response.scoreValue)
        assertEquals("BRONCE", response.levelName)
        assertEquals(50, response.maxLoanAmount)
        assertEquals(listOf("Completaste tu perfil sin dejar campos vacíos"), response.reasons)
    }

    @Test
    fun `perfil medio da PLATA con razones`() {
        // 72*.21 + 100*.14 + 35*.105 + 55*.105 + 40*.14 = 15.12 + 14 + 3.675 + 5.775 + 5.6 = 44.17 -> 44
        val response = service.calculate(request("1c", "2d", "3b", "4c", "5c"))
        assertEquals(44, response.scoreValue)
        assertEquals("PLATA", response.levelName)
        assertEquals(300, response.maxLoanAmount)
        assertTrue("Tu actividad como comercio y ventas sumó puntos" in response.reasons)
        assertTrue("Llevas tiempo en lo mismo, eso genera confianza" in response.reasons)
        assertEquals("Completaste tu perfil sin dejar campos vacíos", response.reasons.last())
    }

    @Test
    fun `caso real 1b 2d 3c 4a 5d da ORO con monto maximo 450`() {
        // 80*.21 + 100*.14 + 70*.105 + 100*.105 + 15*.14 = 50.75 -> 51
        val response = service.calculate(request("1b", "2d", "3c", "4a", "5d"))
        assertEquals(51, response.scoreValue)
        assertEquals("ORO", response.levelName)
        assertEquals(450, response.maxLoanAmount)
    }

    @Test
    fun `limites de categoria y monto`() {
        assertEquals(ScoreLevel.BRONZE, ScoreLevel.forScore(0))
        assertEquals(ScoreLevel.BRONZE, ScoreLevel.forScore(25))
        assertEquals(ScoreLevel.SILVER, ScoreLevel.forScore(26))
        assertEquals(ScoreLevel.SILVER, ScoreLevel.forScore(50))
        assertEquals(ScoreLevel.GOLD, ScoreLevel.forScore(51))
        assertEquals(ScoreLevel.GOLD, ScoreLevel.forScore(100))
    }

    @Test
    fun `redondeo en los cortes 25_5 y 50_5`() {
        // Puntaje exacto en milésimas -> nivel tras redondear (mitades hacia arriba)
        val cases = listOf(
            25_499 to ScoreLevel.BRONZE,
            25_500 to ScoreLevel.SILVER,
            50_499 to ScoreLevel.SILVER,
            50_500 to ScoreLevel.GOLD
        )
        cases.forEach { (thousandths, expected) ->
            assertEquals(expected, ScoreLevel.forScore(service.roundScore(thousandths)), "$thousandths")
        }
    }

    @Test
    fun `monto maximo es fijo por nivel`() {
        assertEquals(listOf(50, 300, 450), ScoreLevel.entries.map { it.maxLoanAmount })
        assertEquals(listOf(50, 50, 50), ScoreLevel.entries.map { it.minLoanAmount })
        (0..100).forEach { score ->
            val level = ScoreLevel.forScore(score)
            val expected = mapOf("BRONCE" to 50, "PLATA" to 300, "ORO" to 450).getValue(level.levelName)
            assertEquals(expected, level.maxLoanAmount, "score $score")
        }
    }

    @Test
    fun `combinaciones reales mas cercanas a los cortes`() {
        // Puntaje exacto -> redondeado; el nivel se decide sobre el redondeado (scoreValue)
        val cases = listOf(
            listOf("1e", "2b", "3b", "4d", "5d") to Triple(25, "BRONCE", 50),  // 25.48, el más alto bajo 25.5
            listOf("1d", "2a", "3b", "4d", "5c") to Triple(26, "PLATA", 300),  // 25.55, el más bajo desde 25.5
            listOf("1g", "2c", "3c", "4a", "5a") to Triple(50, "PLATA", 300),  // 50.47, el más alto bajo 50.5
            listOf("1e", "2b", "3d", "4b", "5a") to Triple(51, "ORO", 450)     // 50.505, el más bajo desde 50.5
        )
        cases.forEach { (codes, expected) ->
            val response = service.calculate(requestOf(codes))
            assertEquals(expected, Triple(response.scoreValue, response.levelName, response.maxLoanAmount), "$codes")
        }
    }

    @Test
    fun `nivel y monto coinciden en todas las combinaciones posibles`() {
        val all = expectedPoints.fold(listOf(emptyList<String>())) { acc, table ->
            acc.flatMap { prefix -> table.keys.map { prefix + it } }
        }
        assertEquals(2048, all.size)
        all.forEach { codes ->
            val response = service.calculate(requestOf(codes))
            val level = ScoreLevel.forScore(response.scoreValue)
            assertEquals(expectedScore(codes), response.scoreValue, "$codes")
            assertEquals(level.levelName, response.levelName, "$codes")
            assertEquals(level.maxLoanAmount, response.maxLoanAmount, "$codes")
        }
    }

    @Test
    fun `codigo invalido lanza IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            service.calculate(request("1z", "2a", "3a", "4a", "5a"))
        }
    }

    @Test
    fun `acepta codigos en mayusculas y con espacios`() {
        val response = service.calculate(request(" 1A", "2D", "3D ", "4A", "5A"))
        assertEquals(68, response.scoreValue)
    }

    // Tabla definitiva: código -> puntos, por campo
    private val expectedPoints = listOf(
        mapOf("1a" to 90, "1b" to 80, "1c" to 72, "1d" to 65, "1e" to 58, "1f" to 50, "1g" to 42, "1h" to 35),
        mapOf("2a" to 0, "2b" to 35, "2c" to 70, "2d" to 100),
        mapOf("3a" to 0, "3b" to 35, "3c" to 70, "3d" to 100),
        mapOf("4a" to 100, "4b" to 85, "4c" to 55, "4d" to 25),
        mapOf("5a" to 100, "5b" to 70, "5c" to 40, "5d" to 15)
    )
    // Pesos en milésimas, mismo orden que los campos
    private val weights = listOf(210, 140, 105, 105, 140)
    private val fields = listOf("employmentType", "employmentTenure", "residentialTenure", "housingType", "dependents")
    private val baseline = listOf("1a", "2a", "3a", "4a", "5a")

    private fun requestOf(codes: List<String>) = request(codes[0], codes[1], codes[2], codes[3], codes[4])

    private fun expectedScore(codes: List<String>): Int {
        val sum = codes.indices.sumOf { expectedPoints[it].getValue(codes[it].lowercase()) * weights[it] }
        return (sum + 500) / 1000
    }

    @Test
    fun `cada codigo de cada campo aporta el puntaje de la tabla`() {
        expectedPoints.forEachIndexed { field, table ->
            table.keys.forEach { code ->
                val codes = baseline.toMutableList().apply { set(field, code) }
                assertEquals(expectedScore(codes), service.calculate(requestOf(codes)).scoreValue, "código $code")
            }
        }
    }

    @Test
    fun `cada codigo se acepta en mayusculas`() {
        expectedPoints.forEachIndexed { field, table ->
            table.keys.forEach { code ->
                val codes = baseline.toMutableList().apply { set(field, code.uppercase()) }
                assertEquals(expectedScore(codes), service.calculate(requestOf(codes)).scoreValue, "código ${code.uppercase()}")
            }
        }
    }

    @Test
    fun `codigo invalido en cualquier campo lista los valores permitidos`() {
        fields.forEachIndexed { field, name ->
            // Código de otra pregunta: formato válido pero no permitido en este campo
            val wrong = if (field == 0) "2a" else "1a"
            val codes = baseline.toMutableList().apply { set(field, wrong) }
            val ex = assertFailsWith<IllegalArgumentException> { service.calculate(requestOf(codes)) }
            assertEquals(
                "Código inválido '$wrong' para $name. Valores permitidos: ${expectedPoints[field].keys.joinToString()}",
                ex.message
            )
        }
    }

    @Test
    fun `comercio y ventas 1c conserva su mensaje`() {
        val response = service.calculate(request("1c", "2a", "3a", "4c", "5c"))
        assertEquals("Tu actividad como comercio y ventas sumó puntos", response.reasons.first())
    }
}
