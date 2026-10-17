package com.join8.identifyr.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "score")
data class ScoreConfig(
    val rules: Rules = Rules(),
    val thresholds: Thresholds = Thresholds()
) {
    data class Rules(
        val minAge: Int = 18,
        val minPhoneConsistency: Int = 3,
        val residentialStabilityMonths: Rule? = Rule(min = 12, points = 10),
        val economicActivityTenureMonths: Rule? = Rule(min = 6, points = 10),
        val dependents: Rule? = Rule(max = 5, points = 5),
        val phoneAgeMonths: Rule? = Rule(min = 12, points = 8),
        val emailAgeMonths: Rule? = Rule(min = 12, points = 8),
        val deviceConsistency: Rule? = Rule(min = 3, points = 6)
    )

    data class Rule(
        val min: Int? = null,
        val max: Int? = null,
        val points: Int = 0
    )

    data class Thresholds(
        val excellent: Int = 80,
        val good: Int = 60,
        val fair: Int = 40,
        val poor: Int = 20,
        val veryPoor: Int = 0
    )
}