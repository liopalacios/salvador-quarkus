package com.join8.identifyr.service

import com.join8.identifyr.model.dto.request.QuestionnaireRequest
import com.join8.identifyr.model.entity.Questionnaire
import java.util.UUID
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

    // --- Bloque 2: pesos fijos por campo, escalados al 30% (cada punto vale 0.3) ---

    private fun questionnaire(
        email: String? = null, address: String? = null, ruc: String? = null, driverLicense: String? = null,
        vehiclePlate: String? = null, educationLevel: String? = null, workplace: String? = null,
        workApps: String? = null, accountStatementPhotoId: String? = null,
        codes: List<String> = listOf("1h", "2a", "3a", "4b", "5a")
    ) = Questionnaire(
        userId = UUID.randomUUID(),
        employmentType = codes[0], employmentTenure = codes[1], residentialTenure = codes[2],
        housingType = codes[3], dependents = codes[4],
        email = email, address = address, ruc = ruc, driverLicense = driverLicense, vehiclePlate = vehiclePlate,
        educationLevel = educationLevel, workplace = workplace, workApps = workApps,
        accountStatementPhotoId = accountStatementPhotoId
    )

    private fun fullProfile(codes: List<String>) = questionnaire(
        "a@b.com", "Av. Siempre Viva 123", "20123456789", "Q12345678", "ABC-123",
        "Ninguno", "Mercado central", "Rappi, Uber", "estado-cuenta-1", codes
    )

    // Base 1h 2a 3a 4b 5a = 35*.21 + 0 + 0 + 85*.105 + 100*.14 = 30.275
    private val baseThousandths = 30_275

    @Test
    fun `sin bloque 2 el score es igual al del calculo base`() {
        assertEquals(service.calculate(request("1h", "2a", "3a", "4b", "5a")), service.calculate(questionnaire()))
    }

    @Test
    fun `pesos del bloque 2 suman 58 y aportan como maximo 17_4`() {
        assertEquals(listOf(5, 8, 6, 6, 5, 4, 6, 10, 8), ProfileField.entries.map { it.weight })
        assertEquals(58, ProfileField.entries.sumOf { it.weight })
        assertEquals(17_400, ProfileField.entries.sumOf { it.thousandths })

        // 30.275 + 17.4 = 47.675 -> 48
        val full = service.calculate(fullProfile(listOf("1h", "2a", "3a", "4b", "5a")))
        assertEquals(48, full.scoreValue)
        assertEquals("PLATA", full.levelName)
        assertEquals(300, full.maxLoanAmount)
        assertEquals(ProfileField.entries.map { it.reason }, full.reasons.takeLast(9))
    }

    @Test
    fun `score maximo posible es 67_9 mas 17_4 = 85`() {
        val max = service.calculate(fullProfile(listOf("1a", "2d", "3d", "4a", "5a")))
        assertEquals(85, max.scoreValue) // 85.3
        assertEquals("ORO", max.levelName)
        assertEquals(450, max.maxLoanAmount)
    }

    @Test
    fun `cada campo del bloque 2 suma su peso por 0_3 con su motivo`() {
        val base = service.calculate(questionnaire())
        val cases = mapOf(
            questionnaire(email = "a@b.com") to (1_500 to "Agregaste tu correo electrónico (+5)"),
            questionnaire(address = "Av. 1") to (2_400 to "Agregaste tu dirección exacta (+8)"),
            questionnaire(ruc = "20123456789") to (1_800 to "Agregaste tu RUC (+6)"),
            questionnaire(driverLicense = "Q1") to (1_800 to "Agregaste tu licencia de conducir (+6)"),
            questionnaire(vehiclePlate = "ABC-123") to (1_500 to "Agregaste la placa de tu vehículo (+5)"),
            questionnaire(educationLevel = "Ninguno") to (1_200 to "Agregaste tu nivel de estudios (+4)"),
            questionnaire(workplace = "Mercado") to (1_800 to "Agregaste tu lugar de trabajo (+6)"),
            questionnaire(workApps = "Rappi") to (3_000 to "Agregaste tus apps de trabajo (+10)"),
            questionnaire(accountStatementPhotoId = "estado-cuenta-x") to (2_400 to "Agregaste tu estado de cuenta (+8)")
        )
        cases.forEach { (q, expected) ->
            val response = service.calculate(q)
            assertEquals(service.roundScore(baseThousandths + expected.first), response.scoreValue, expected.second)
            assertEquals(base.reasons + expected.second, response.reasons)
        }
    }

    @Test
    fun `se redondea una sola vez sobre la suma exacta`() {
        // email + workApps = 15 * 0.3 = 4.5 -> 30.275 + 4.5 = 34.775 -> 35
        assertEquals(35, service.calculate(questionnaire(email = "a@b.com", workApps = "Rappi")).scoreValue)
        // address = 2.4 -> 32.675 -> 33 (redondeando por separado daría 30 + 2 = 32)
        assertEquals(33, service.calculate(questionnaire(address = "Av. 1")).scoreValue)
    }

    @Test
    fun `campos vacios del bloque 2 no suman`() {
        val base = service.calculate(questionnaire())
        assertEquals(base, service.calculate(questionnaire(email = "", workApps = "   ")))
    }

    @Test
    fun `el bloque 2 recalcula nivel y monto`() {
        // 1g 2c 3c 4a 5a = 50.47 -> 50 PLATA; + email 1.5 = 51.97 -> 52 ORO
        val codes = listOf("1g", "2c", "3c", "4a", "5a")
        assertEquals("PLATA", service.calculate(questionnaire(codes = codes)).levelName)
        val response = service.calculate(questionnaire(email = "a@b.com", codes = codes))
        assertEquals(52, response.scoreValue)
        assertEquals("ORO", response.levelName)
        assertEquals(450, response.maxLoanAmount)
    }

    @Test
    fun `normalizeCode valida y normaliza codigos del bloque 1`() {
        assertEquals("1h", service.normalizeCode("employmentType", " 1H "))
        assertFailsWith<IllegalArgumentException> { service.normalizeCode("dependents", "5z") }
    }

    @Test
    fun `comercio y ventas 1c conserva su mensaje`() {
        val response = service.calculate(request("1c", "2a", "3a", "4c", "5c"))
        assertEquals("Tu actividad como comercio y ventas sumó puntos", response.reasons.first())
    }
}
