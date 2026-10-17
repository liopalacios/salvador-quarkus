package com.join8.identifyr.model.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "digital_stability")
data class DigitalStability (
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "phone_age_months")
    val phoneAgeMonths: Int? = null,

    @Column(name = "email_age_months")
    val emailAgeMonths: Int? = null,

    @Column(name = "device_tenure_months")
    val deviceTenureMonths: Int? = null,

    @Column(name = "device_consistency_count")
    val deviceConsistencyCount: Int = 1,

    @Column(name = "last_device_id", length = 255)
    val lastDeviceId: String? = null,

    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)