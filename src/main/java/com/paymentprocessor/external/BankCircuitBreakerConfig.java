package com.paymentprocessor.external;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;

import java.time.Duration;

@Configuration
public class BankCircuitBreakerConfig {
    @Bean
    public org.springframework.cloud.client.circuitbreaker.Customizer<Resilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)                     // abre se 50%+ das chamadas falharem
                        .slidingWindowSize(10)                        // considera as últimas 10 chamadas
                        .minimumNumberOfCalls(5)                      // só decide depois de 5 chamadas
                        .waitDurationInOpenState(Duration.ofSeconds(10)) // fica "aberto" 10s antes de testar de novo
                        .permittedNumberOfCallsInHalfOpenState(3)     // testa com 3 chamadas no half-open
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(2))       // timeout por chamada
                        .build())
                .build());
    }
}
