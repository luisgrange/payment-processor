package com.paymentprocessor.messaging;

import com.paymentprocessor.domain.entities.Payment;
import com.paymentprocessor.messaging.dto.PaymentCreatedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentProducer {
    private final RabbitTemplate rabbitTemplate;

    public PaymentProducer(RabbitTemplate rabbitTemplate){
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishPaymentCreated(Payment payment){
        PaymentCreatedEvent event = PaymentCreatedEvent.from(payment);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.PAYMENTS_EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_CREATED,
                event
        );
    }

    public void publishPaymentOk(Payment payment){
        PaymentCreatedEvent event = PaymentCreatedEvent.from(payment);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.PAYMENTS_EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_OK,
                event
        );
    }
}
