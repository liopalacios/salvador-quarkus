package com.join8.identifyr.repository

import com.join8.identifyr.model.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRepository : JpaRepository<User, UUID> {
    fun existsByPhotoFront(photoFront: String): Boolean
    fun existsByPhotoBack(photoBack: String): Boolean
    fun existsByEmail(email: String): Boolean
    fun existsByPhone(phone: String): Boolean
    fun findByEmail(email: String): User?
}