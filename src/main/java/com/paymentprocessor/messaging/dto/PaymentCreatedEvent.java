package com.paymentprocessor.messaging.dto;

import com.paymentprocessor.domain.entities.Payment;

import java.math.BigDecimal;

public record PaymentCreatedEvent(
        String externalId,
        String customerId,
        BigDecimal amount
) {
    public static PaymentCreatedEvent from(Payment payment){
        return new PaymentCreatedEvent(
                payment.getExternalId(),
                payment.getCustomerId(),
                payment.getAmount()
        );
    }
}
