package com.join8.identifyr.repository

import com.join8.identifyr.model.entity.Loan
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface LoanRepository : JpaRepository<Loan, UUID> {
    fun findByLoanRequestId(loanRequestId: UUID): Loan?
    fun existsByOperationCode(operationCode: String): Boolean
}
