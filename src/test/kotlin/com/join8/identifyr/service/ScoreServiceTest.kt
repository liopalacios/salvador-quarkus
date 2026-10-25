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
        assertEquals(450, response.maxLoanAmount) // 50 + 17/19*450 = 452.6 -> 450
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
        assertEquals(250, response.maxLoanAmount) // 50 + 18/24*250 = 237.5 -> 250
        assertTrue("Tu actividad como comercio y ventas sumó puntos" in response.reasons)
        assertTrue("Llevas tiempo en lo mismo, eso genera confianza" in response.reasons)
        assertEquals("Completaste tu perfil sin dejar campos vacíos", response.reasons.last())
    }

    @Test
    fun `limites de categoria y monto`() {
        assertEquals("BRONCE" to 50, service.levelAndAmount(25))
        assertEquals("PLATA" to 50, service.levelAndAmount(26))
        assertEquals("PLATA" to 200, service.levelAndAmount(38)) // 175 -> 200
        assertEquals("PLATA" to 300, service.levelAndAmount(50))
        assertEquals("ORO" to 50, service.levelAndAmount(51))
        assertEquals("ORO" to 500, service.levelAndAmount(70))
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
