package com.join8.identifyr.controller

import com.join8.identifyr.model.entity.DniPhotoSide
import com.join8.identifyr.model.entity.User
import com.join8.identifyr.repository.DniPhotoRepository
import com.join8.identifyr.repository.UserRepository
import com.join8.identifyr.support.TestTokens
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.support.TransactionTemplate
import java.util.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** POST /users/{userId}/dni-photos (multipart): guarda las imágenes en dni_photos y sus ids en el usuario. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DniPhotoUploadTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var dniPhotoRepository: DniPhotoRepository
    @Autowired lateinit var transactionTemplate: TransactionTemplate
    @Value("\${jwt.secret}") lateinit var jwtSecret: String

    private lateinit var user: User
    private lateinit var other: User

    @BeforeEach
    fun createUsers() {
        user = userRepository.save(User(phone = "987654321"))
        other = userRepository.save(User(phone = "912345678"))
    }

    @AfterEach
    fun cleanUp() {
        dniPhotoRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun bearer(u: User) = "Bearer ${TestTokens.user(jwtSecret, u.id!!, u.phone)}"

    private fun upload(
        userId: UUID,
        authorization: String?,
        vararg parts: MockMultipartFile = arrayOf(part("front", JPEG), part("back", PNG))
    ): ResultActions {
        val request = multipart("/users/$userId/dni-photos")
        parts.forEach { request.file(it) }
        authorization?.let { request.header(HttpHeaders.AUTHORIZATION, it) }
        return mockMvc.perform(request)
    }

    private fun part(name: String, bytes: ByteArray, contentType: String = MediaType.IMAGE_JPEG_VALUE) =
        MockMultipartFile(name, "$name.jpg", contentType, bytes)

    // Lee las fotos dentro de una transacción (el contenido es LAZY)
    private fun photosOf(userId: UUID) = transactionTemplate.execute {
        dniPhotoRepository.findAllByUserId(userId).associate { it.side to (it.id.toString() to it.content.copyOf()) }
    }!!

    @Test
    fun `guarda las dos imagenes y escribe sus ids en el usuario`() {
        upload(user.id!!, bearer(user))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(user.id.toString()))

        val photos = photosOf(user.id!!)
        val saved = userRepository.findById(user.id!!).get()
        assertEquals(photos[DniPhotoSide.FRONT]!!.first, saved.photoFront)
        assertEquals(photos[DniPhotoSide.BACK]!!.first, saved.photoBack)
        assertContentEquals(JPEG, photos[DniPhotoSide.FRONT]!!.second)
        assertContentEquals(PNG, photos[DniPhotoSide.BACK]!!.second)
    }

    @Test
    fun `el response trae los mismos ids que quedan en el usuario`() {
        val body = upload(user.id!!, bearer(user)).andReturn().response.contentAsString
        val saved = userRepository.findById(user.id!!).get()
        assert(body.contains(saved.photoFront!!) && body.contains(saved.photoBack!!))
    }

    @Test
    fun `subirlas de nuevo reemplaza las anteriores`() {
        upload(user.id!!, bearer(user)).andExpect(status().isOk)
        val firstFront = userRepository.findById(user.id!!).get().photoFront

        upload(user.id!!, bearer(user)).andExpect(status().isOk)
        val saved = userRepository.findById(user.id!!).get()
        assertNotEquals(firstFront, saved.photoFront)
        assertEquals(2, dniPhotoRepository.findAllByUserId(user.id!!).size)
    }

    @Test
    fun `sin token 401 y con token de otro usuario 403`() {
        upload(user.id!!, null).andExpect(status().isUnauthorized)
        upload(user.id!!, bearer(other)).andExpect(status().isForbidden)
        assertEquals(0, dniPhotoRepository.count())
    }

    @Test
    fun `falta una parte 400`() {
        upload(user.id!!, bearer(user), part("front", JPEG)).andExpect(status().isBadRequest)
        assertEquals(0, dniPhotoRepository.count())
    }

    @Test
    fun `archivo que no es imagen 400 aunque diga image jpeg`() {
        upload(user.id!!, bearer(user), part("front", "hola".toByteArray()), part("back", PNG))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("La foto frontal debe ser JPEG o PNG"))
        assertNull(userRepository.findById(user.id!!).get().photoFront)
    }

    @Test
    fun `archivo vacio 400`() {
        upload(user.id!!, bearer(user), part("front", JPEG), part("back", ByteArray(0)))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `usuario inexistente 404`() {
        val ghost = User(id = UUID.randomUUID(), phone = "900000001")
        upload(ghost.id!!, bearer(ghost)).andExpect(status().isNotFound)
    }

    private companion object {
        val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
        val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 4, 5, 6)
    }
}
