package com.github.ajharry69.kcb_b2c_payment.payment.dto;

import com.github.ajharry69.kcb_b2c_payment.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;


public record PaymentResponse(
        UUID paymentId, // Internal system ID
        String transactionId, // Client-provided ID
        String recipientPhoneNumber,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String mnoReference, // Reference from MNO on success
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}