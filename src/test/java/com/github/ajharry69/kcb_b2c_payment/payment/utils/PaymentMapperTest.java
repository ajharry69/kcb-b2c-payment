package com.github.ajharry69.kcb_b2c_payment.payment.utils;

import com.github.ajharry69.kcb_b2c_payment.payment.dto.PaymentRequest;
import com.github.ajharry69.kcb_b2c_payment.payment.dto.PaymentResponse;
import com.github.ajharry69.kcb_b2c_payment.payment.model.Payment;
import com.github.ajharry69.kcb_b2c_payment.payment.model.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMapperTest {

    private PaymentMapper paymentMapper;

    @BeforeEach
    void setUp() {
        paymentMapper = Mappers.getMapper(PaymentMapper.class);
    }

    @Test
    void shouldMapPaymentRequestToEntity() {
        PaymentRequest request = new PaymentRequest(
                "TXN12345",
                "+254712345678",
                new BigDecimal("150.75"),
                "KES"
        );

        Payment entity = paymentMapper.toEntity(request);

        assertThat(entity).isNotNull();
        assertThat(entity.getId()).isNull();
        assertThat(entity.getTransactionId()).isEqualTo(request.transactionId());
        assertThat(entity.getRecipientPhoneNumber()).isEqualTo(request.recipientPhoneNumber());
        assertThat(entity.getAmount()).isEqualTo(request.amount());
        assertThat(entity.getCurrency()).isEqualTo(request.currency());
        assertThat(entity.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(entity.getFailureReason()).isNull();
        assertThat(entity.getMnoReference()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getUpdatedAt()).isNull();
    }

    @Test
    void shouldMapPaymentEntityToResponse() {
        UUID paymentId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Payment entity = Payment.builder()
                .id(paymentId)
                .transactionId("TXN67890")
                .recipientPhoneNumber("+254798765432")
                .amount(new BigDecimal("2000.00"))
                .currency("KES")
                .status(PaymentStatus.SUCCESSFUL)
                .mnoReference("MNO_REF_ABC")
                .failureReason(null)
                .createdAt(now.minusMinutes(5))
                .updatedAt(now)
                .build();

        PaymentResponse response = paymentMapper.toResponse(entity);

        assertThat(response).isNotNull();
        assertThat(response.paymentId()).isEqualTo(entity.getId());
        assertThat(response.transactionId()).isEqualTo(entity.getTransactionId());
        assertThat(response.recipientPhoneNumber()).isEqualTo(entity.getRecipientPhoneNumber());
        assertThat(response.amount()).isEqualTo(entity.getAmount());
        assertThat(response.currency()).isEqualTo(entity.getCurrency());
        assertThat(response.status()).isEqualTo(entity.getStatus());
        assertThat(response.mnoReference()).isEqualTo(entity.getMnoReference());
        assertThat(response.failureReason()).isEqualTo(entity.getFailureReason());
        assertThat(response.createdAt()).isEqualTo(entity.getCreatedAt());
        assertThat(response.updatedAt()).isEqualTo(entity.getUpdatedAt());
    }

    @Test
    void shouldMapFailedPaymentEntityToResponse() {
        UUID paymentId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Payment entity = Payment.builder()
                .id(paymentId)
                .transactionId("TXN_FAIL_01")
                .recipientPhoneNumber("+254711111111")
                .amount(new BigDecimal("50.00"))
                .currency("KES")
                .status(PaymentStatus.FAILED)
                .mnoReference(null)
                .failureReason("Insufficient funds")
                .createdAt(now.minusMinutes(10))
                .updatedAt(now.minusMinutes(1))
                .build();

        PaymentResponse response = paymentMapper.toResponse(entity);

        assertThat(response).isNotNull();
        assertThat(response.paymentId()).isEqualTo(entity.getId());
        assertThat(response.transactionId()).isEqualTo(entity.getTransactionId());
        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.mnoReference()).isNull();
        assertThat(response.failureReason()).isEqualTo("Insufficient funds");
        assertThat(response.createdAt()).isEqualTo(entity.getCreatedAt());
        assertThat(response.updatedAt()).isEqualTo(entity.getUpdatedAt());
    }
}
