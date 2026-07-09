package com.paymentprocessor.messaging;

import com.paymentprocessor.domain.PaymentRepository;
import com.paymentprocessor.domain.entities.Payment;
import com.paymentprocessor.domain.enums.PaymentStatus;
import com.paymentprocessor.external.PaymentBankClient;
import com.paymentprocessor.messaging.dto.PaymentCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentConsumer {
    private static final Logger log = LoggerFactory.getLogger(PaymentConsumer.class);

    private final PaymentRepository paymentRepository;
    private final PaymentBankClient paymentBankClient;
    private final PaymentProducer paymentProducer;

    public PaymentConsumer(PaymentRepository paymentRepository,
                           PaymentBankClient paymentBankClient,
                           PaymentProducer paymentProducer) {
        this.paymentRepository = paymentRepository;
        this.paymentBankClient = paymentBankClient;
        this.paymentProducer = paymentProducer;
    }

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_PROCESS_QUEUE)
    public void handle(PaymentCreatedEvent event) {

        Payment payment = paymentRepository.findByExternalId(event.externalId())
                .orElseThrow(() -> new IllegalStateException(
                        "Payment não encontrado para externalId=" + event.externalId()));

        // idempotência no consumer: se já foi processado, ignora silenciosamente
        if (payment.getStatus() != PaymentStatus.PENDING){
            log.warn("Payment {} já está em status {}, ignorando mensagem duplicada",
                    event.externalId(), payment.getStatus());
            return;
        }

        payment.markAsProcessing();
        paymentRepository.save(payment);

        // chamada ao "banco" — pode lançar exception, o que dispara o retry
        // configurado no RabbitRetryConfig (2s, 4s, 8s -> depois DLQ)
        paymentBankClient.charge(payment.getCustomerId(), payment.getAmount());

        payment.markAsSuccess();
        paymentRepository.save(payment);

        paymentProducer.publishPaymentOk(payment);

        log.info("Payment {} processado com sucesso", event.externalId());
    }

}
