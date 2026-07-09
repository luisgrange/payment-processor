package com.paymentprocessor.api;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String externalId) {
        super("Payment não encontrado para externalId=" + externalId);
    }
}
