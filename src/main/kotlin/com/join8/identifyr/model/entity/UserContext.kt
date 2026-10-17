package com.join8.identifyr.model.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "user_context")
data class UserContext (
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "birth_date", nullable = false)
    val birthDate: LocalDate,

    @Column(length = 500)
    val address: String? = null,

    @Column(length = 100)
    val district: String? = null,

    @Column(name = "housing_type", length = 50)
    val housingType: String? = null, // OWNED, RENTED, FAMILY, ASSIGNED, OTHER

    @Column(name = "residential_stability_months")
    val residentialStabilityMonths: Int? = null,

    @Column(name = "education_level", length = 50)
    val educationLevel: String? = null, // PRIMARY, SECONDARY, TECHNICAL, UNIVERSITY, POSTGRADUATE, NONE

    @Column(name = "dependents_count")
    val dependentsCount: Int = 0,

    @Column(name = "economic_activity", length = 100)
    val economicActivity: String? = null,

    @Column(name = "economic_activity_type", length = 50)
    val economicActivityType: String? = null, // COMMERCE, DELIVERY, TAXI, INDEPENDENT, EMPLOYED, UNEMPLOYED, OTHER

    @Column(name = "economic_activity_tenure_months")
    val economicActivityTenureMonths: Int? = null,

    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)