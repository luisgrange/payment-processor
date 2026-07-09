package com.paymentprocessor.external;

public class BankUnavailableException extends RuntimeException {
    public BankUnavailableException(String message) {
        super(message);
    }
}
