package com.join8.identifyr.repository

import com.join8.identifyr.model.entity.Loan
import com.join8.identifyr.model.entity.LoanStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface LoanRepository : JpaRepository<Loan, UUID> {
    fun findByLoanRequestId(loanRequestId: UUID): Loan?

    // FOR UPDATE: registrar y confirmar pagos del mismo préstamo no se pisan
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loan l where l.id = :id")
    fun findByIdForUpdate(id: UUID): Loan?
    fun existsByOperationCode(operationCode: String): Boolean
    fun findFirstByPhoneAndStatusOrderByCreatedAtDesc(phone: String, status: LoanStatus): Loan?
    fun existsByUserIdAndStatus(userId: UUID, status: LoanStatus): Boolean
    /** Último préstamo cancelado del usuario (regla de 48 h). */
    fun findFirstByUserIdAndStatusOrderByClosedAtDesc(userId: UUID, status: LoanStatus): Loan?
    /** Préstamos vigentes aún sin recalcular con el motor de crédito. */
    fun findAllByStatusAndEngineVersionIsNull(status: LoanStatus): List<Loan>
}
