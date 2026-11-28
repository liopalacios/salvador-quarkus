package com.join8.identifyr.repository

import com.join8.identifyr.model.entity.LoanRequest
import com.join8.identifyr.model.entity.LoanRequestStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

@Repository
interface LoanRequestRepository : JpaRepository<LoanRequest, UUID> {
    // SELECT ... FOR UPDATE: serializa el registro de préstamos sobre la misma solicitud
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LoanRequest l where l.id = :id")
    fun findByIdForUpdate(id: UUID): LoanRequest?

    fun existsByUserIdAndStatus(userId: UUID, status: LoanRequestStatus): Boolean
    fun findAllByUserId(userId: UUID): List<LoanRequest>
    fun findFirstByPhoneAndStatusOrderByCreatedAtDesc(phone: String, status: LoanRequestStatus): LoanRequest?
    // Última solicitud (cualquier estado): el inicio muestra la rechazada y la campana avisa del rechazo
    fun findFirstByPhoneOrderByCreatedAtDesc(phone: String): LoanRequest?
    fun findFirstByUserIdOrderByCreatedAtDesc(userId: UUID): LoanRequest?

    // Listado del supervisor: estado + rango de created_at [from, to) en SQL y el orden de cada pestaña.
    // El id desempata para que el orden sea estable. Usa idx_loan_request_status_created_at.
    @Query("select l from LoanRequest l where l.status = :status and l.createdAt >= :from and l.createdAt < :to order by l.createdAt asc, l.id asc")
    fun findPendingForList(status: LoanRequestStatus, from: Instant, to: Instant): List<LoanRequest>

    @Query("select l from LoanRequest l where l.status = :status and l.createdAt >= :from and l.createdAt < :to order by l.processedAt desc nulls last, l.id asc")
    fun findProcessedForList(status: LoanRequestStatus, from: Instant, to: Instant): List<LoanRequest>

    @Query("select l from LoanRequest l where l.status = :status and l.createdAt >= :from and l.createdAt < :to order by l.updatedAt desc, l.id asc")
    fun findRejectedForList(status: LoanRequestStatus, from: Instant, to: Instant): List<LoanRequest>
}
