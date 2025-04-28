package com.github.ajharry69.kcb_b2c_payment.payment.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotBlank(message = "Transaction ID cannot be blank")
        @Size(min = 1, max = 50, message = "Transaction ID length must be between 1 and 50")
        String transactionId,

        @NotBlank(message = "Recipient phone number cannot be blank")
        @Pattern(regexp = "^\\+?[0-9. ()-]{7,25}$", message = "Invalid phone number format")
        String recipientPhoneNumber,

        @NotNull(message = "Amount cannot be null")
        @DecimalMin(value = "0.01", message = "Amount must be positive")
        @Digits(integer = 10, fraction = 2, message = "Invalid amount format (max 10 integer, 2 fraction digits)")
        BigDecimal amount,

        @NotBlank(message = "Currency cannot be blank")
        @Size(min = 3, max = 3, message = "Currency must be a 3-letter code (e.g., KES)")
        String currency
) {
}