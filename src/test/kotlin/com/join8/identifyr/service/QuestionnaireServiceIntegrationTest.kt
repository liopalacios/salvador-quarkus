package com.join8.identifyr.service

import com.join8.identifyr.model.dto.request.QuestionnaireRegisterRequest
import com.join8.identifyr.model.entity.Questionnaire
import com.join8.identifyr.model.entity.User
import com.join8.identifyr.repository.QuestionnaireRepository
import com.join8.identifyr.repository.UserRepository
import org.junit.jupiter.api.AfterEach
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.util.*
import kotlin.test.*

@SpringBootTest
@ActiveProfiles("test")
class QuestionnaireServiceIntegrationTest {

    @Autowired lateinit var service: QuestionnaireService
    @Autowired lateinit var userRepository: UserRepository
    @MockitoSpyBean lateinit var questionnaireRepository: QuestionnaireRepository

    @AfterEach
    fun cleanUp() {
        reset(questionnaireRepository)
        questionnaireRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun newUser(): UUID = userRepository.save(
        User(
            phone = "9${(10_000_000..99_999_999).random()}",
            email = "",
            photoFront = UUID.randomUUID().toString(),
            photoBack = UUID.randomUUID().toString(),
            passwordHash = ""
        )
    ).id!!

    private fun block1(userId: UUID) = QuestionnaireRegisterRequest(
        userId = userId.toString(),
        employmentType = "1h",
        employmentTenure = "2a",
        residentialTenure = "3a",
        housingType = "4b",
        dependents = "5a"
    )

    private fun history(userId: UUID) = questionnaireRepository.findAllByUserIdOrderByIdAsc(userId)

    @Test
    fun `primer registro crea un registro activo`() {
        val userId = newUser()
        val response = service.register(block1(userId))

        assertEquals("A", response.status)
        assertEquals(userId, response.userId)
        val rows = history(userId)
        assertEquals(1, rows.size)
        assertEquals(response.questionnaireId, rows.single().id)
        assertEquals("A", rows.single().estado)
        assertEquals("1h", rows.single().employmentType)
    }

    @Test
    fun `segundo envio inactiva el anterior intacto y crea uno nuevo combinado`() {
        val userId = newUser()
        val first = service.register(block1(userId))
        val second = service.register(
            QuestionnaireRegisterRequest(
                userId = userId.toString(), email = "ana@correo.com",
                educationLevel = "Técnico", workApps = "Rappi, Uber"
            )
        )

        assertNotEquals(first.questionnaireId, second.questionnaireId)
        val (old, new) = history(userId)
        // Registro anterior: solo cambia el estado
        assertEquals("I", old.estado)
        assertEquals("1h", old.employmentType)
        assertNull(old.email)
        assertNull(old.workApps)
        // Registro nuevo: bloque 1 + bloque 2
        assertEquals("A", new.estado)
        assertEquals(listOf("1h", "2a", "3a", "4b", "5a"),
            listOf(new.employmentType, new.employmentTenure, new.residentialTenure, new.housingType, new.dependents))
        assertEquals("ana@correo.com", new.email)
        assertEquals("Técnico", new.educationLevel)
        assertEquals("Rappi, Uber", new.workApps)
        assertEquals(1, questionnaireRepository.countByUserIdAndEstado(userId, "A"))
    }

    @Test
    fun `tercer envio reemplaza el campo repetido y conserva el resto`() {
        val userId = newUser()
        service.register(block1(userId))
        service.register(QuestionnaireRegisterRequest(userId = userId.toString(), email = "a@b.com", ruc = "20123456789"))
        service.register(QuestionnaireRegisterRequest(userId = userId.toString(), email = "otro@b.com", housingType = "4A"))

        val rows = history(userId)
        assertEquals(listOf("I", "I", "A"), rows.map { it.estado })
        val active = rows.last()
        assertEquals("otro@b.com", active.email)
        assertEquals("20123456789", active.ruc)
        assertEquals("4a", active.housingType)
        assertEquals(1, questionnaireRepository.countByUserIdAndEstado(userId, "A"))
    }

    @Test
    fun `email y workApps suman 4_5 y un campo repetido no cuenta dos veces`() {
        val userId = newUser()
        service.register(block1(userId))
        val base = service.scoreFor(userId)

        service.register(QuestionnaireRegisterRequest(userId = userId.toString(), email = "a@b.com", workApps = "Rappi"))
        val withProfile = service.scoreFor(userId)
        // (5 + 10) * 0.3 = 4.5 -> 30.275 + 4.5 = 34.775 -> 35
        assertEquals(30, base.scoreValue)
        assertEquals(35, withProfile.scoreValue)
        assertTrue("Agregaste tu correo electrónico (+5)" in withProfile.reasons)
        assertTrue("Agregaste tus apps de trabajo (+10)" in withProfile.reasons)

        service.register(QuestionnaireRegisterRequest(userId = userId.toString(), email = "otro@b.com"))
        assertEquals(withProfile, service.scoreFor(userId))
    }

    @Test
    fun `si falla el insert se hace rollback y el registro anterior sigue activo`() {
        val userId = newUser()
        val first = service.register(block1(userId))

        // Falla al insertar el registro nuevo, después de haber pasado el anterior a 'I'
        doThrow(RuntimeException("fallo simulado"))
            .`when`(questionnaireRepository).saveAndFlush(argThat<Questionnaire> { it.id == null })

        val ex = assertFailsWith<RuntimeException> {
            service.register(QuestionnaireRegisterRequest(userId = userId.toString(), email = "a@b.com"))
        }
        assertEquals("fallo simulado", ex.message)
        // El registro anterior sí llegó a pasar a 'I' (flush) antes del fallo
        verify(questionnaireRepository).saveAndFlush(argThat<Questionnaire> { it.id == first.questionnaireId && it.estado == "I" })

        reset(questionnaireRepository)
        val rows = history(userId)
        assertEquals(1, rows.size)
        assertEquals(first.questionnaireId, rows.single().id)
        assertEquals("A", rows.single().estado)
    }

    @Test
    fun `primer registro sin las 5 respuestas obligatorias falla sin guardar nada`() {
        val userId = newUser()
        val ex = assertFailsWith<IllegalArgumentException> {
            service.register(QuestionnaireRegisterRequest(userId = userId.toString(), email = "a@b.com"))
        }
        assertTrue(ex.message!!.contains("employmentType"))
        assertTrue(history(userId).isEmpty())
    }

    @Test
    fun `validacion fallida en un segundo envio no toca el registro activo`() {
        val userId = newUser()
        service.register(block1(userId))
        assertFailsWith<IllegalArgumentException> {
            service.register(QuestionnaireRegisterRequest(userId = userId.toString(), ruc = "123"))
        }
        assertEquals(listOf("A"), history(userId).map { it.estado })
    }
}
