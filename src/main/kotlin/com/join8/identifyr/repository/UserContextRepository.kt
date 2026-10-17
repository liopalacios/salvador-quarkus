package com.join8.identifyr.repository

import com.join8.identifyr.model.entity.User
import com.join8.identifyr.model.entity.UserContext
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserContextRepository : JpaRepository<UserContext, UUID> {
    fun findByUser(user: User): UserContext?
    fun existsByUser(user: User): Boolean
}