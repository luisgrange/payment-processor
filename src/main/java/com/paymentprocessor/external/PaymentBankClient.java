package com.paymentprocessor.external;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class PaymentBankClient {
    private static final Logger log = LoggerFactory.getLogger(PaymentBankClient.class);
    private static final String CIRCUIT_BREAKER_ID = "bankCharge";

    private final CircuitBreaker circuitBreaker;

    public PaymentBankClient(CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.circuitBreaker = circuitBreakerFactory.create(CIRCUIT_BREAKER_ID);
    }

    public void charge(String customerId, BigDecimal amount) {
        circuitBreaker.run(
                () -> {
                    doCharge(customerId, amount);
                    return null;
                },
                throwable -> {
                    // fallback: loga e relança, pra continuar acionando o retry do RabbitMQ
                    log.error("Circuit breaker acionado ao cobrar customerId={}: {}",
                            customerId, throwable.getMessage());
                    throw new BankChargeException("Falha ao processar cobrança", throwable);
                }
        );
    }

    /**
     * Simula uma chamada HTTP a um banco emissor real.
     * 30% de chance de falhar, simulando timeout/indisponibilidade transiente.
     */
    private void doCharge(String customerId, BigDecimal amount) {
        simulateNetworkLatency();

        boolean shouldFail = ThreadLocalRandom.current().nextInt(100) < 30;
        if (shouldFail) {
            throw new BankUnavailableException(
                    "Banco indisponível ao cobrar customerId=" + customerId);
        }

        log.info("Cobrança de {} aprovada para customerId={}", amount, customerId);
    }

    private void simulateNetworkLatency() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(100, 400));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
