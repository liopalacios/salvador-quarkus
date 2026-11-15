package com.join8.identifyr.repository

import com.join8.identifyr.model.entity.LoanInstallment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface LoanInstallmentRepository : JpaRepository<LoanInstallment, UUID> {
    fun findAllByLoanIdOrderByInstallmentNumberAsc(loanId: UUID): List<LoanInstallment>
}
