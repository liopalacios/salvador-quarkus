package com.join8.identifyr.repository

import com.join8.identifyr.model.entity.LoginAttempt
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface LoginAttemptRepository : JpaRepository<LoginAttempt, UUID> {
    fun findAllByPhoneOrderByCreatedAtAsc(phone: String): List<LoginAttempt>
}
