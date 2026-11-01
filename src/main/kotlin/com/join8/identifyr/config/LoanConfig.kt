package com.join8.identifyr.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal
import java.time.Clock
import java.time.ZoneId

/** Condiciones del préstamo (prefijo "loan" en application.yaml). */
@ConfigurationProperties(prefix = "loan")
data class LoanProperties(
    /** Tasa sobre el monto: interés = floor(monto * tasa). */
    val interestRate: BigDecimal = BigDecimal("0.18"),
    /** Plazo real de pago: fechaPago = día de registro + estos días. */
    val paymentTermDays: Long = 7,
    /** Plazo que muestra la app (requisito de Google Play): fechaPagoApp = día de registro + estos días. */
    val appPaymentTermDays: Long = 61,
    /** Zona horaria con la que se determina el "día de registro". */
    val zone: ZoneId = ZoneId.of("America/Lima")
)

@Configuration
@EnableConfigurationProperties(LoanProperties::class)
class LoanConfig {
    // Inyectable para poder fijar la hora en los tests
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
