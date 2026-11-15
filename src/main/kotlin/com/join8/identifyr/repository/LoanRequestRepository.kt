package com.join8.identifyr.repository

import com.join8.identifyr.model.entity.LoanRequest
import com.join8.identifyr.model.entity.LoanRequestStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
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

    // Listado del supervisor: un filtro por estado en SQL y el orden de cada pestaña.
    // El id desempata para que el orden sea estable.
    fun findAllByStatusOrderByCreatedAtAscIdAsc(status: LoanRequestStatus): List<LoanRequest>
    fun findAllByStatusOrderByProcessedAtDescIdAsc(status: LoanRequestStatus): List<LoanRequest>
    fun findAllByStatusOrderByUpdatedAtDescIdAsc(status: LoanRequestStatus): List<LoanRequest>
}
