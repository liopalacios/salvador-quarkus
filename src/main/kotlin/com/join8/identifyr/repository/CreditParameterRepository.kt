package com.join8.identifyr.repository

import com.join8.identifyr.model.entity.CreditParameter
import com.join8.identifyr.model.entity.Holiday
import com.join8.identifyr.model.entity.LoanPaymentItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.*

@Repository
interface CreditParameterRepository : JpaRepository<CreditParameter, Long> {
    fun findAllByCode(code: String): List<CreditParameter>
}

@Repository
interface HolidayRepository : JpaRepository<Holiday, LocalDate>

@Repository
interface LoanPaymentItemRepository : JpaRepository<LoanPaymentItem, UUID> {
    fun findAllByPaymentId(paymentId: UUID): List<LoanPaymentItem>
}
