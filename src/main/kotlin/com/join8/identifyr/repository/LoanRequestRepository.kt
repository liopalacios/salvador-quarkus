package com.join8.identifyr.repository

import com.join8.identifyr.model.entity.LoanRequest
import com.join8.identifyr.model.entity.LoanRequestStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

interface LoanRequestStatusCount {
    val status: LoanRequestStatus
    val total: Long
}

@Repository
interface LoanRequestRepository : JpaRepository<LoanRequest, UUID> {
    // SELECT ... FOR UPDATE: serializa el registro de préstamos sobre la misma solicitud
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LoanRequest l where l.id = :id")
    fun findByIdForUpdate(id: UUID): LoanRequest?

    fun existsByUserIdAndStatus(userId: UUID, status: LoanRequestStatus): Boolean
    fun findAllByUserId(userId: UUID): List<LoanRequest>

    // El orden llega en el Pageable (depende del estado)
    fun findAllByStatus(status: LoanRequestStatus, pageable: Pageable): Page<LoanRequest>

    // Conteos de las tres pestañas en una sola consulta; los estados sin filas no aparecen
    @Query("select l.status as status, count(l) as total from LoanRequest l group by l.status")
    fun countGroupByStatus(): List<LoanRequestStatusCount>
}
