package com.paymentprocessor.api;

public class PaymentAlreadyExistsException extends RuntimeException {
    public PaymentAlreadyExistsException(String externalId) {
        super("Payment já existe para externalId=" + externalId);
    }
}
