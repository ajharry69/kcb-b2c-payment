package com.github.ajharry69.kcb_b2c_payment.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class MMOServiceException extends RuntimeException {
    public MMOServiceException(String message) {
        super("MNO service interaction failed: " + message);
    }

    public MMOServiceException(String message, Throwable cause) {
        super("MNO service interaction failed: " + message, cause);
    }
}
