package com.github.ajharry69.kcb_b2c_payment.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;


@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateTransactionException extends RuntimeException {
    public DuplicateTransactionException(String transactionId) {
        super("Duplicate transaction ID: " + transactionId + ". Payment already exists or is being processed.");
    }
}
