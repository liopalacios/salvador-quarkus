package com.join8.identifyr.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

/**
 * Prefijo "loan" en application.yaml. Tasas, comisión, APIs y feriados NO van aquí: viven en las tablas
 * credit_parameter y holiday (ver [com.join8.identifyr.service.credit.CreditParameterService]).
 */
@ConfigurationProperties(prefix = "loan")
data class LoanProperties(
    /** Zona horaria con la que se determina la fecha de desembolso. */
    val zone: ZoneId = ZoneId.of("America/Lima")
)

@Configuration
@EnableConfigurationProperties(LoanProperties::class)
class LoanConfig {
    // Inyectable para poder fijar la hora en los tests
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
