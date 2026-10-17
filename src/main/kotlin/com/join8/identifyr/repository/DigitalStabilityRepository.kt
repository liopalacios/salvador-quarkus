package com.join8.identifyr.repository

import com.join8.identifyr.model.entity.DigitalStability
import com.join8.identifyr.model.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface DigitalStabilityRepository : JpaRepository<DigitalStability, UUID> {
    fun findByUser(user: User): DigitalStability?
    fun existsByUser(user: User): Boolean
}