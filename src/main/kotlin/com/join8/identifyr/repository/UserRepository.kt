package com.join8.identifyr.repository

import com.join8.identifyr.model.entity.User
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRepository : JpaRepository<User, UUID> {
    fun existsByPhotoFront(photoFront: String): Boolean
    fun existsByPhotoBack(photoBack: String): Boolean
    fun existsByPhotoFrontAndIdNot(photoFront: String, id: UUID): Boolean
    fun existsByPhotoBackAndIdNot(photoBack: String, id: UUID): Boolean
    fun existsByEmail(email: String): Boolean
    fun existsByPhone(phone: String): Boolean
    fun findByEmail(email: String): User?

    // SELECT ... FOR UPDATE sobre el usuario: serializa los registros de cuestionario concurrentes
    // del mismo usuario, incluido el primero (cuando aún no hay fila activa que bloquear).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    fun findByIdForUpdate(id: UUID): User?

    // FOR UPDATE: logins simultáneos del mismo celular no pierden incrementos del contador de fallos.
    // Lista (no User?) por si quedaran celulares duplicados de antes de uq_users_phone: se usa el más antiguo.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.phone = :phone order by u.createdAt asc")
    fun findAllByPhoneForUpdate(phone: String): List<User>
}
