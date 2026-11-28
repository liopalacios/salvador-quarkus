package com.join8.identifyr.model.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

/** Parámetro configurable del motor de crédito con vigencia [validFrom, validTo] (hoja 1_Parametros). */
@Entity
@Table(name = "credit_parameter")
class CreditParameter(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    val id: Long? = null,

    @Column(name = "code", length = 30, nullable = false)
    val code: String,

    @Column(name = "param_value", nullable = false, precision = 20, scale = 10)
    val value: BigDecimal,

    @Column(name = "valid_from", nullable = false)
    val validFrom: LocalDate,

    @Column(name = "valid_to")
    val validTo: LocalDate? = null,

    @Column(name = "description", length = 255)
    val description: String? = null
)

/** Feriado nacional (hoja 7_Feriados): los vencimientos que caen aquí pasan al siguiente día hábil. */
@Entity
@Table(name = "holiday")
class Holiday(
    @Id
    @Column(name = "holiday_date", nullable = false)
    val date: LocalDate,

    @Column(name = "description", length = 100, nullable = false)
    val description: String
)
