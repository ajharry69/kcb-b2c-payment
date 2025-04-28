package com.github.ajharry69.kcb_b2c_payment.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;


@ResponseStatus(HttpStatus.NOT_FOUND)
public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(UUID paymentId) {
        super("Payment not found with ID: " + paymentId);
    }

    public PaymentNotFoundException(String transactionId) {
        super("Payment not found with Transaction ID: " + transactionId);
    }
}
