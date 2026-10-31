package com.join8.identifyr.repository

import com.join8.identifyr.model.entity.Questionnaire
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface QuestionnaireRepository : JpaRepository<Questionnaire, Long> {
    fun findByUserIdAndEstado(userId: UUID, estado: String): Questionnaire?
    fun findAllByUserIdOrderByIdAsc(userId: UUID): List<Questionnaire>
    fun countByUserIdAndEstado(userId: UUID, estado: String): Long
}
