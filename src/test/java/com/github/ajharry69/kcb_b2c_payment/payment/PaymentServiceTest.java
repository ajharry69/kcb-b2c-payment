package com.github.ajharry69.kcb_b2c_payment.payment;

import com.github.ajharry69.kcb_b2c_payment.exceptions.DuplicateTransactionException;
import com.github.ajharry69.kcb_b2c_payment.exceptions.MMOServiceException;
import com.github.ajharry69.kcb_b2c_payment.exceptions.PaymentNotFoundException;
import com.github.ajharry69.kcb_b2c_payment.mmo.MobileMoneyService;
import com.github.ajharry69.kcb_b2c_payment.notification.SmsService;
import com.github.ajharry69.kcb_b2c_payment.payment.dto.PaymentRequest;
import com.github.ajharry69.kcb_b2c_payment.payment.dto.PaymentResponse;
import com.github.ajharry69.kcb_b2c_payment.payment.model.Payment;
import com.github.ajharry69.kcb_b2c_payment.payment.model.PaymentStatus;
import com.github.ajharry69.kcb_b2c_payment.payment.utils.PaymentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private MobileMoneyService mobileMoneyService;
    @Mock
    private SmsService smsService;
    @Mock
    private PaymentMapper paymentMapper;

    @InjectMocks
    private PaymentService paymentService;

    private PaymentRequest validRequest;
    private Payment pendingPaymentEntity;
    private Payment processingPaymentEntity;
    private Payment successfulPaymentEntity;
    private Payment failedPaymentEntity;
    private PaymentResponse successfulResponse;
    private PaymentResponse failedResponse;
    private final UUID paymentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        validRequest = new PaymentRequest("TXN123", "+254712345678", new BigDecimal("100.00"), "KES");

        pendingPaymentEntity = Payment.builder()
                .id(paymentId)
                .transactionId(validRequest.transactionId())
                .recipientPhoneNumber(validRequest.recipientPhoneNumber())
                .amount(validRequest.amount())
                .currency(validRequest.currency())
                .status(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now().minusSeconds(10))
                .updatedAt(LocalDateTime.now().minusSeconds(10))
                .build();

        processingPaymentEntity = Payment.builder()
                .id(paymentId)
                .transactionId(validRequest.transactionId())
                .recipientPhoneNumber(validRequest.recipientPhoneNumber())
                .amount(validRequest.amount())
                .currency(validRequest.currency())
                .status(PaymentStatus.PROCESSING)
                .createdAt(pendingPaymentEntity.getCreatedAt())
                .updatedAt(LocalDateTime.now().minusSeconds(5))
                .build();

        successfulPaymentEntity = Payment.builder()
                .id(paymentId)
                .transactionId(validRequest.transactionId())
                .recipientPhoneNumber(validRequest.recipientPhoneNumber())
                .amount(validRequest.amount())
                .currency(validRequest.currency())
                .status(PaymentStatus.SUCCESSFUL)
                .mnoReference("MNO_SUCCESS_REF")
                .createdAt(pendingPaymentEntity.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();

        failedPaymentEntity = Payment.builder()
                .id(paymentId)
                .transactionId(validRequest.transactionId())
                .recipientPhoneNumber(validRequest.recipientPhoneNumber())
                .amount(validRequest.amount())
                .currency(validRequest.currency())
                .status(PaymentStatus.FAILED)
                .failureReason("Insufficient Funds")
                .createdAt(pendingPaymentEntity.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();

        successfulResponse = new PaymentResponse(paymentId, validRequest.transactionId(), validRequest.recipientPhoneNumber(), validRequest.amount(), validRequest.currency(), PaymentStatus.SUCCESSFUL, "MNO_SUCCESS_REF", null, successfulPaymentEntity.getCreatedAt(), successfulPaymentEntity.getUpdatedAt());
        failedResponse = new PaymentResponse(paymentId, validRequest.transactionId(), validRequest.recipientPhoneNumber(), validRequest.amount(), validRequest.currency(), PaymentStatus.FAILED, null, "Insufficient Funds", failedPaymentEntity.getCreatedAt(), failedPaymentEntity.getUpdatedAt());

        lenient().when(paymentMapper.toEntity(any(PaymentRequest.class)))
                .thenReturn(pendingPaymentEntity);
        lenient().when(paymentMapper.toResponse(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment p = invocation.getArgument(0);
                    return new PaymentResponse(p.getId(), p.getTransactionId(), p.getRecipientPhoneNumber(), p.getAmount(), p.getCurrency(), p.getStatus(), p.getMnoReference(), p.getFailureReason(), p.getCreatedAt(), p.getUpdatedAt());
                });

        lenient().when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment p = invocation.getArgument(0);
                    if (p.getId() == null) p.setId(paymentId);
                    p.setCreatedAt(LocalDateTime.now().minusSeconds(10));
                    p.setUpdatedAt(LocalDateTime.now().minusSeconds(10));
                    return p;
                });
        lenient().when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment p = invocation.getArgument(0);
                    if (p.getId() == null) p.setId(paymentId);
                    p.setUpdatedAt(LocalDateTime.now().minusSeconds(5));
                    return p;
                });
    }

    @Nested
    @DisplayName("Initiate Payment Tests")
    class InitiatePayment {

        @Test
        @DisplayName("Should initiate payment successfully and return PROCESSING status")
        void initiatePayment_Success() {
            when(paymentRepository.findByTransactionId(validRequest.transactionId()))
                    .thenReturn(Optional.empty());
            CompletableFuture<Payment> mnoFuture = CompletableFuture.completedFuture(successfulPaymentEntity);
            when(mobileMoneyService.processB2CPayment(any(Payment.class)))
                    .thenReturn(mnoFuture);
            when(paymentRepository.findById(paymentId))
                    .thenReturn(Optional.of(processingPaymentEntity));
            when(paymentRepository.saveAndFlush(any(Payment.class)))
                    .thenReturn(pendingPaymentEntity);
            when(paymentRepository.save(any(Payment.class)))
                    .thenReturn(processingPaymentEntity);

            PaymentResponse response = paymentService.initiatePayment(validRequest);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(PaymentStatus.PROCESSING);
            assertThat(response.transactionId()).isEqualTo(validRequest.transactionId());
            assertThat(response.paymentId()).isEqualTo(paymentId);

            verify(paymentRepository).findByTransactionId(validRequest.transactionId());
            verify(paymentMapper).toEntity(validRequest);
            verify(paymentRepository).saveAndFlush(any(Payment.class));
            verify(paymentRepository, times(2)).save(any(Payment.class));
            verify(mobileMoneyService).processB2CPayment(any(Payment.class));
            verify(smsService).sendSuccessNotification(any(Payment.class));
            verify(smsService, never()).sendFailureNotification(any(Payment.class));
        }

        @Test
        @DisplayName("Should throw DuplicateTransactionException for existing PENDING transaction")
        void initiatePayment_DuplicatePending() {
            pendingPaymentEntity.setStatus(PaymentStatus.PENDING);
            when(paymentRepository.findByTransactionId(validRequest.transactionId())).thenReturn(Optional.of(pendingPaymentEntity));

            assertThatThrownBy(() -> paymentService.initiatePayment(validRequest))
                    .isInstanceOf(DuplicateTransactionException.class)
                    .hasMessageContaining(validRequest.transactionId());

            verify(paymentMapper, never()).toEntity(any());
            verify(paymentRepository, never()).saveAndFlush(any());
            verify(paymentRepository, never()).save(any());
            verify(mobileMoneyService, never()).processB2CPayment(any());
        }

        @Test
        @DisplayName("Should throw DuplicateTransactionException for existing PROCESSING transaction")
        void initiatePayment_DuplicateProcessing() {
            processingPaymentEntity.setStatus(PaymentStatus.PROCESSING);
            when(paymentRepository.findByTransactionId(validRequest.transactionId())).thenReturn(Optional.of(processingPaymentEntity));

            assertThatThrownBy(() -> paymentService.initiatePayment(validRequest))
                    .isInstanceOf(DuplicateTransactionException.class)
                    .hasMessageContaining(validRequest.transactionId());

            verify(paymentMapper, never()).toEntity(any());
            verify(paymentRepository, never()).saveAndFlush(any());
            verify(paymentRepository, never()).save(any());
            verify(mobileMoneyService, never()).processB2CPayment(any());
        }


        @Test
        @DisplayName("Should return existing SUCCESSFUL status for completed transaction")
        void initiatePayment_AlreadySuccessful() {
            when(paymentRepository.findByTransactionId(validRequest.transactionId())).thenReturn(Optional.of(successfulPaymentEntity));
            when(paymentMapper.toResponse(successfulPaymentEntity)).thenReturn(successfulResponse);

            PaymentResponse response = paymentService.initiatePayment(validRequest);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESSFUL);
            assertThat(response.transactionId()).isEqualTo(validRequest.transactionId());
            assertThat(response.paymentId()).isEqualTo(paymentId);
            verify(paymentMapper, never()).toEntity(any());
            verify(paymentRepository, never()).saveAndFlush(any());
            verify(paymentRepository, never()).save(any());
            verify(mobileMoneyService, never()).processB2CPayment(any());
            verify(smsService, never()).sendSuccessNotification(any());
            verify(smsService, never()).sendFailureNotification(any());
        }

        @Test
        @DisplayName("Should return existing FAILED status for completed transaction")
        void initiatePayment_AlreadyFailed() {
            when(paymentRepository.findByTransactionId(validRequest.transactionId())).thenReturn(Optional.of(failedPaymentEntity));
            when(paymentMapper.toResponse(failedPaymentEntity)).thenReturn(failedResponse);

            PaymentResponse response = paymentService.initiatePayment(validRequest);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(response.transactionId()).isEqualTo(validRequest.transactionId());
            assertThat(response.paymentId()).isEqualTo(paymentId);

            verify(paymentMapper, never()).toEntity(any());
            verify(paymentRepository, never()).saveAndFlush(any());
            verify(paymentRepository, never()).save(any());
            verify(mobileMoneyService, never()).processB2CPayment(any());
            verify(smsService, never()).sendSuccessNotification(any());
            verify(smsService, never()).sendFailureNotification(any());
        }


        @Test
        @DisplayName("Should handle MNO processing failure and send failure SMS")
        void initiatePayment_MnoFailure() {
            when(paymentRepository.findByTransactionId(validRequest.transactionId()))
                    .thenReturn(Optional.empty());
            CompletableFuture<Payment> mnoFuture = CompletableFuture.completedFuture(failedPaymentEntity);
            when(mobileMoneyService.processB2CPayment(any(Payment.class)))
                    .thenReturn(mnoFuture);
            when(paymentRepository.findById(paymentId))
                    .thenReturn(Optional.of(processingPaymentEntity));
            when(paymentRepository.saveAndFlush(any(Payment.class)))
                    .thenReturn(pendingPaymentEntity);
            when(paymentRepository.save(any(Payment.class)))
                    .thenReturn(processingPaymentEntity);

            PaymentResponse response = paymentService.initiatePayment(validRequest);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(PaymentStatus.PROCESSING);

            verify(paymentRepository)
                    .findByTransactionId(validRequest.transactionId());
            verify(paymentRepository)
                    .saveAndFlush(any(Payment.class));
            verify(paymentRepository, times(2))
                    .save(any(Payment.class));
            verify(mobileMoneyService)
                    .processB2CPayment(any(Payment.class));
            verify(smsService, never())
                    .sendSuccessNotification(any(Payment.class));
            verify(smsService)
                    .sendFailureNotification(any(Payment.class));
        }

        @Test
        @DisplayName("Should handle MNO communication exception during submission")
        void initiatePayment_MnoSubmissionError() {
            when(paymentRepository.findByTransactionId(validRequest.transactionId()))
                    .thenReturn(Optional.empty());
            var submissionException = new RuntimeException("Network timeout connecting to MNO");
            when(mobileMoneyService.processB2CPayment(any(Payment.class)))
                    .thenThrow(submissionException);
            when(paymentRepository.saveAndFlush(any(Payment.class)))
                    .thenReturn(pendingPaymentEntity);
            when(paymentRepository.save(any(Payment.class)))
                    .thenReturn(processingPaymentEntity)
                    .thenReturn(failedPaymentEntity);

            assertThatThrownBy(() -> paymentService.initiatePayment(validRequest))
                    .isInstanceOf(MMOServiceException.class)
                    .hasMessageContaining("Failed to submit payment to MNO")
                    .hasCause(submissionException);

            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository, times(2)).save(paymentCaptor.capture());
            assertThat(paymentCaptor.getAllValues().get(1).getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(paymentCaptor.getAllValues().get(1).getFailureReason()).contains("Network timeout");

            verify(smsService, never()).sendSuccessNotification(any());
            verify(smsService, never()).sendFailureNotification(any());
        }

        @Test
        @DisplayName("Should handle MNO future completing exceptionally")
        void initiatePayment_MnoFutureException() {
            when(paymentRepository.findByTransactionId(validRequest.transactionId()))
                    .thenReturn(Optional.empty());
            when(paymentRepository.findById(paymentId))
                    .thenReturn(Optional.of(processingPaymentEntity));
            CompletableFuture<Payment> mnoFuture = new CompletableFuture<>();
            mnoFuture.completeExceptionally(new CompletionException("Internal MNO Error", new RuntimeException("Simulated cause")));
            when(mobileMoneyService.processB2CPayment(any(Payment.class)))
                    .thenReturn(mnoFuture);
            when(paymentRepository.saveAndFlush(any(Payment.class)))
                    .thenReturn(pendingPaymentEntity);
            when(paymentRepository.save(any(Payment.class)))
                    .thenReturn(processingPaymentEntity);

            PaymentResponse response = paymentService.initiatePayment(validRequest);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(PaymentStatus.PROCESSING);

            verify(paymentRepository).findByTransactionId(validRequest.transactionId());
            verify(paymentRepository).saveAndFlush(any(Payment.class));
            verify(paymentRepository, times(2)).save(any(Payment.class));
            verify(mobileMoneyService).processB2CPayment(any(Payment.class));

            assertThatThrownBy(mnoFuture::join).isInstanceOf(CompletionException.class);

            ArgumentCaptor<Payment> smsPaymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(smsService, never()).sendSuccessNotification(any());
            verify(smsService).sendFailureNotification(smsPaymentCaptor.capture());

            assertThat(smsPaymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(smsPaymentCaptor.getValue().getFailureReason()).contains("MNO communication error");
        }
    }

    @Nested
    @DisplayName("Get Payment Tests")
    class GetPayment {

        @Test
        @DisplayName("Should return payment by ID successfully")
        void getPaymentById_Success() {
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(successfulPaymentEntity));
            when(paymentMapper.toResponse(successfulPaymentEntity)).thenReturn(successfulResponse);

            PaymentResponse response = paymentService.getPaymentById(paymentId);

            assertThat(response).isNotNull();
            assertThat(response).isEqualTo(successfulResponse);
            verify(paymentRepository).findById(paymentId);
            verify(paymentMapper).toResponse(successfulPaymentEntity);
        }

        @Test
        @DisplayName("Should throw PaymentNotFoundException for non-existent ID")
        void getPaymentById_NotFound() {
            UUID nonExistentId = UUID.randomUUID();
            when(paymentRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPaymentById(nonExistentId))
                    .isInstanceOf(PaymentNotFoundException.class)
                    .hasMessageContaining(nonExistentId.toString());

            verify(paymentRepository).findById(nonExistentId);
            verify(paymentMapper, never()).toResponse(any());
        }

        @Test
        @DisplayName("Should return payment by Transaction ID successfully")
        void getPaymentByTransactionId_Success() {
            String txnId = successfulPaymentEntity.getTransactionId();
            when(paymentRepository.findByTransactionId(txnId)).thenReturn(Optional.of(successfulPaymentEntity));
            when(paymentMapper.toResponse(successfulPaymentEntity)).thenReturn(successfulResponse);

            PaymentResponse response = paymentService.getPaymentByTransactionId(txnId);

            assertThat(response).isNotNull();
            assertThat(response).isEqualTo(successfulResponse);
            verify(paymentRepository).findByTransactionId(txnId);
            verify(paymentMapper).toResponse(successfulPaymentEntity);
        }

        @Test
        @DisplayName("Should throw PaymentNotFoundException for non-existent Transaction ID")
        void getPaymentByTransactionId_NotFound() {
            String nonExistentTxnId = "TXN_NOT_FOUND";
            when(paymentRepository.findByTransactionId(nonExistentTxnId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPaymentByTransactionId(nonExistentTxnId))
                    .isInstanceOf(PaymentNotFoundException.class)
                    .hasMessageContaining(nonExistentTxnId);

            verify(paymentRepository).findByTransactionId(nonExistentTxnId);
            verify(paymentMapper, never()).toResponse(any());
        }
    }
}
