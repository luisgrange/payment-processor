package com.paymentprocessor.api.dto;

import com.paymentprocessor.domain.entities.Payment;
import com.paymentprocessor.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse (
        String externalId,
        String customerId,
        BigDecimal amount,
        PaymentStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getExternalId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
