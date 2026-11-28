package com.join8.identifyr.repository

import com.join8.identifyr.model.entity.LoanPayment
import com.join8.identifyr.model.entity.PaymentStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface LoanPaymentRepository : JpaRepository<LoanPayment, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from LoanPayment p where p.id = :id")
    fun findByIdForUpdate(id: UUID): LoanPayment?

    fun findFirstByLoanIdAndStatus(loanId: UUID, status: PaymentStatus): LoanPayment?
    fun existsByOperationCodeAndStatusNot(operationCode: String, status: PaymentStatus): Boolean

    /** Listado del supervisor: PENDIENTE el más antiguo primero; los revisados, el último revisado primero. */
    fun findAllByStatusOrderByCreatedAtAscIdAsc(status: PaymentStatus): List<LoanPayment>
    fun findAllByStatusOrderByReviewedAtDescIdAsc(status: PaymentStatus): List<LoanPayment>

    /** Pagos rechazados que el cliente aún no vio (notificación PAYMENT_REJECTED). */
    fun findAllByUserIdAndStatusAndRejectionSeenAtIsNullOrderByReviewedAtDesc(userId: UUID, status: PaymentStatus): List<LoanPayment>
}
