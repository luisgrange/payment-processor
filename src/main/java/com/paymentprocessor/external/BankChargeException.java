package com.paymentprocessor.external;

public class BankChargeException extends RuntimeException {
    public BankChargeException(String message, Throwable cause) {
        super(message, cause);
    }
}
