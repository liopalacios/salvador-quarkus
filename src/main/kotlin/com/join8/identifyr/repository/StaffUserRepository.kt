package com.join8.identifyr.repository

import com.join8.identifyr.model.entity.StaffUser
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface StaffUserRepository : JpaRepository<StaffUser, UUID> {

    // FOR UPDATE: logins simultáneos del mismo usuario no pierden incrementos del contador de fallos.
    // Sin distinguir mayúsculas (índice único sobre LOWER(username) en V10).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StaffUser s where lower(s.username) = lower(:username)")
    fun findByUsernameForUpdate(username: String): StaffUser?
}
