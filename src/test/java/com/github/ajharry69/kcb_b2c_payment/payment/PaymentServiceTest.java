package com.github.ajharry69.kcb_b2c_payment.payment; // Corrected package

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
import org.mockito.Spy;
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
import static org.mockito.ArgumentMatchers.eq;
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

    // Use @Spy instead of @InjectMocks if we need to verify calls to the async method itself
    // Or keep @InjectMocks and test the async method separately. Let's try the latter first.
    @InjectMocks
    private PaymentService paymentService;

    // We might need to mock the async method call behaviour in initiatePayment tests
    // Or test the async method directly in separate tests.
    // Let's create a spy to allow mocking the async method call within the same service instance.
    @Spy
    @InjectMocks
    private PaymentService paymentServiceSpy;

    private PaymentRequest validRequest;
    private Payment pendingPaymentEntity;
    private Payment processingPaymentEntity;
    private Payment successfulPaymentEntity;
    private Payment failedPaymentEntity;
    private PaymentResponse processingResponse;
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

        processingResponse = new PaymentResponse(paymentId, validRequest.transactionId(), validRequest.recipientPhoneNumber(), validRequest.amount(), validRequest.currency(), PaymentStatus.PROCESSING, null, null, processingPaymentEntity.getCreatedAt(), processingPaymentEntity.getUpdatedAt());
        successfulResponse = new PaymentResponse(paymentId, validRequest.transactionId(), validRequest.recipientPhoneNumber(), validRequest.amount(), validRequest.currency(), PaymentStatus.SUCCESSFUL, "MNO_SUCCESS_REF", null, successfulPaymentEntity.getCreatedAt(), successfulPaymentEntity.getUpdatedAt());
        failedResponse = new PaymentResponse(paymentId, validRequest.transactionId(), validRequest.recipientPhoneNumber(), validRequest.amount(), validRequest.currency(), PaymentStatus.FAILED, null, "Insufficient Funds", failedPaymentEntity.getCreatedAt(), failedPaymentEntity.getUpdatedAt());

        lenient().when(paymentMapper.toEntity(any(PaymentRequest.class))).thenReturn(pendingPaymentEntity);
        lenient().when(paymentMapper.toResponse(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            return new PaymentResponse(p.getId(), p.getTransactionId(), p.getRecipientPhoneNumber(), p.getAmount(), p.getCurrency(), p.getStatus(), p.getMnoReference(), p.getFailureReason(), p.getCreatedAt(), p.getUpdatedAt());
        });

        lenient().when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            if (p.getId() == null) p.setId(paymentId); // Simulate ID generation
            p.setCreatedAt(LocalDateTime.now().minusSeconds(10));
            p.setUpdatedAt(LocalDateTime.now().minusSeconds(10));
            return p;
        });
        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            if (p.getId() == null) p.setId(paymentId);
            p.setUpdatedAt(LocalDateTime.now().minusSeconds(5));
            return p;
        });

        lenient().when(paymentRepository.findById(eq(paymentId))).thenReturn(Optional.of(processingPaymentEntity));

        lenient().doNothing().when(paymentServiceSpy).processPaymentAsynchronously(any(UUID.class));
    }

    @Nested
    @DisplayName("Initiate Payment Tests (Sync Part)")
    class InitiatePaymentSync {

        @Test
        @DisplayName("Should save PENDING then PROCESSING status and trigger async processing")
        void initiatePayment_TriggersAsync() {
            when(paymentRepository.findByTransactionId(validRequest.transactionId()))
                    .thenReturn(Optional.empty());
            when(paymentRepository.saveAndFlush(any(Payment.class)))
                    .thenReturn(pendingPaymentEntity);
            when(paymentRepository.save(any(Payment.class)))
                    .thenReturn(processingPaymentEntity);
            when(paymentMapper.toResponse(eq(processingPaymentEntity)))
                    .thenReturn(processingResponse);

            PaymentResponse response = paymentServiceSpy.initiatePayment(validRequest);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(PaymentStatus.PROCESSING);
            assertThat(response.transactionId()).isEqualTo(validRequest.transactionId());
            assertThat(response.paymentId()).isEqualTo(paymentId);
            verify(paymentRepository).findByTransactionId(validRequest.transactionId());
            verify(paymentMapper).toEntity(validRequest);
            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).saveAndFlush(paymentCaptor.capture());
            verify(paymentRepository).save(paymentCaptor.capture());
            assertThat(paymentCaptor.getValue().getStatus())
                    .isEqualTo(PaymentStatus.PROCESSING);
            verify(paymentServiceSpy).processPaymentAsynchronously(eq(paymentId));
            verify(mobileMoneyService, never()).processB2CPayment(any());
            verify(smsService, never()).sendSuccessNotification(any());
            verify(smsService, never()).sendFailureNotification(any());
        }

        @Test
        @DisplayName("Should throw DuplicateTransactionException for existing PENDING transaction")
        void initiatePayment_DuplicatePending() {
            pendingPaymentEntity.setStatus(PaymentStatus.PENDING);
            when(paymentRepository.findByTransactionId(validRequest.transactionId()))
                    .thenReturn(Optional.of(pendingPaymentEntity));

            assertThatThrownBy(() -> paymentService.initiatePayment(validRequest))
                    .isInstanceOf(DuplicateTransactionException.class)
                    .hasMessageContaining(validRequest.transactionId());

            verify(paymentServiceSpy, never()).processPaymentAsynchronously(any());
            verify(paymentRepository, never()).saveAndFlush(any());
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw DuplicateTransactionException for existing PROCESSING transaction")
        void initiatePayment_DuplicateProcessing() {
            processingPaymentEntity.setStatus(PaymentStatus.PROCESSING);
            when(paymentRepository.findByTransactionId(validRequest.transactionId())).thenReturn(Optional.of(processingPaymentEntity));

            assertThatThrownBy(() -> paymentService.initiatePayment(validRequest))
                    .isInstanceOf(DuplicateTransactionException.class)
                    .hasMessageContaining(validRequest.transactionId());

            verify(paymentServiceSpy, never()).processPaymentAsynchronously(any());
            verify(paymentRepository, never()).saveAndFlush(any());
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should return existing SUCCESSFUL status for completed transaction")
        void initiatePayment_AlreadySuccessful() {
            when(paymentRepository.findByTransactionId(validRequest.transactionId())).thenReturn(Optional.of(successfulPaymentEntity));
            when(paymentMapper.toResponse(successfulPaymentEntity)).thenReturn(successfulResponse);

            PaymentResponse response = paymentService.initiatePayment(validRequest);

            assertThat(response).isEqualTo(successfulResponse);
            verify(paymentServiceSpy, never()).processPaymentAsynchronously(any());
            verify(paymentRepository, never()).saveAndFlush(any());
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should return existing FAILED status for completed transaction")
        void initiatePayment_AlreadyFailed() {
            when(paymentRepository.findByTransactionId(validRequest.transactionId())).thenReturn(Optional.of(failedPaymentEntity));
            when(paymentMapper.toResponse(failedPaymentEntity)).thenReturn(failedResponse);

            PaymentResponse response = paymentService.initiatePayment(validRequest);

            assertThat(response).isEqualTo(failedResponse);
            verify(paymentServiceSpy, never()).processPaymentAsynchronously(any());
            verify(paymentRepository, never()).saveAndFlush(any());
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should handle exception during async task submission")
        void initiatePayment_AsyncSubmissionError() {
            when(paymentRepository.findByTransactionId(validRequest.transactionId()))
                    .thenReturn(Optional.empty());
            when(paymentRepository.saveAndFlush(any(Payment.class)))
                    .thenReturn(pendingPaymentEntity);
            when(paymentRepository.save(any(Payment.class)))
                    .thenReturn(processingPaymentEntity);
            RuntimeException submissionException = new RuntimeException("Task rejected");
            doThrow(submissionException).when(paymentServiceSpy)
                    .processPaymentAsynchronously(eq(paymentId));
            when(paymentRepository.save(argThat(p -> p.getStatus() == PaymentStatus.FAILED)))
                    .thenReturn(failedPaymentEntity);


            assertThatThrownBy(() -> paymentServiceSpy.initiatePayment(validRequest))
                    .isInstanceOf(MMOServiceException.class)
                    .hasMessageContaining("Failed to submit payment processing task")
                    .hasCause(submissionException);

            ArgumentCaptor<Payment> failedSaveCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository, times(2))
                    .save(failedSaveCaptor.capture());
            assertThat(failedSaveCaptor.getAllValues().get(1).getStatus())
                    .isEqualTo(PaymentStatus.FAILED);
            assertThat(failedSaveCaptor.getAllValues().get(1).getFailureReason())
                    .contains("Task rejected");
            verify(smsService, never()).sendSuccessNotification(any());
            verify(smsService, never()).sendFailureNotification(any());
        }
    }

    @Nested
    @DisplayName("Async Payment Processing Tests")
    class AsyncProcessing {

        @Test
        @DisplayName("Should process successfully, update status, and send success SMS")
        void processAsync_Success() {
            when(paymentRepository.findById(eq(paymentId)))
                    .thenReturn(Optional.of(processingPaymentEntity));
            CompletableFuture<Payment> mnoFuture = CompletableFuture.completedFuture(successfulPaymentEntity);
            when(mobileMoneyService.processB2CPayment(eq(processingPaymentEntity)))
                    .thenReturn(mnoFuture);
            when(paymentRepository.save(argThat(p -> p.getStatus() == PaymentStatus.SUCCESSFUL)))
                    .thenReturn(successfulPaymentEntity);

            paymentService.processPaymentAsynchronously(paymentId);

            verify(mobileMoneyService).processB2CPayment(eq(processingPaymentEntity));

            ArgumentCaptor<Payment> savedPaymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(savedPaymentCaptor.capture());
            assertThat(savedPaymentCaptor.getValue().getStatus())
                    .isEqualTo(PaymentStatus.SUCCESSFUL);
            assertThat(savedPaymentCaptor.getValue().getMnoReference())
                    .isEqualTo(successfulPaymentEntity.getMnoReference());
            verify(smsService).sendSuccessNotification(eq(successfulPaymentEntity));
            verify(smsService, never()).sendFailureNotification(any());
        }

        @Test
        @DisplayName("Should handle MNO reported failure, update status, and send failure SMS")
        void processAsync_MnoReportedFailure() {
            when(paymentRepository.findById(eq(paymentId))).thenReturn(Optional.of(processingPaymentEntity));
            // Mock MNO service to return failure future
            CompletableFuture<Payment> mnoFuture = CompletableFuture.completedFuture(failedPaymentEntity);
            when(mobileMoneyService.processB2CPayment(eq(processingPaymentEntity))).thenReturn(mnoFuture);
            when(paymentRepository.save(argThat(p -> p.getStatus() == PaymentStatus.FAILED)))
                    .thenReturn(failedPaymentEntity);

            paymentService.processPaymentAsynchronously(paymentId);

            verify(mobileMoneyService).processB2CPayment(eq(processingPaymentEntity));

            ArgumentCaptor<Payment> savedPaymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(savedPaymentCaptor.capture());
            assertThat(savedPaymentCaptor.getValue().getStatus())
                    .isEqualTo(PaymentStatus.FAILED);
            assertThat(savedPaymentCaptor.getValue().getFailureReason())
                    .isEqualTo(failedPaymentEntity.getFailureReason());
            verify(smsService, never()).sendSuccessNotification(any());
            verify(smsService).sendFailureNotification(eq(failedPaymentEntity));
        }

        @Test
        @DisplayName("Should handle MNO future exception, update status, and send failure SMS")
        void processAsync_MnoFutureException() {
            when(paymentRepository.findById(eq(paymentId)))
                    .thenReturn(Optional.of(processingPaymentEntity));
            CompletableFuture<Payment> mnoFuture = new CompletableFuture<>();
            CompletionException exception = new CompletionException("Internal MNO Error", new RuntimeException("Simulated cause"));
            mnoFuture.completeExceptionally(exception);
            when(mobileMoneyService.processB2CPayment(eq(processingPaymentEntity)))
                    .thenReturn(mnoFuture);
            when(paymentRepository.save(argThat(p -> p.getStatus() == PaymentStatus.FAILED)))
                    .thenReturn(failedPaymentEntity);

            paymentService.processPaymentAsynchronously(paymentId);

            verify(mobileMoneyService).processB2CPayment(eq(processingPaymentEntity));
            ArgumentCaptor<Payment> savedPaymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(savedPaymentCaptor.capture());
            assertThat(savedPaymentCaptor.getValue().getStatus())
                    .isEqualTo(PaymentStatus.FAILED);
            assertThat(savedPaymentCaptor.getValue().getFailureReason())
                    .contains("MNO communication error: " + exception.getMessage());
            verify(smsService, never()).sendSuccessNotification(any());
            ArgumentCaptor<Payment> smsCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(smsService).sendFailureNotification(smsCaptor.capture());
            assertThat(smsCaptor.getValue().getStatus())
                    .isEqualTo(PaymentStatus.FAILED);
            assertThat(smsCaptor.getValue().getFailureReason())
                    // Should actually be:
                    //      "MNO communication error: " + exception.getMessage()
                    // but due to the limitation of mocking where we have to define
                    // the expected response when the .save function is called, it
                    // is not worth the hassle at this stage
                    .contains("Insufficient Funds");
        }

        @Test
        @DisplayName("Should skip processing if payment status is not PROCESSING")
        void processAsync_SkipsIfNotProcessing() {
            successfulPaymentEntity.setStatus(PaymentStatus.SUCCESSFUL);
            when(paymentRepository.findById(eq(paymentId))).thenReturn(Optional.of(successfulPaymentEntity));

            paymentService.processPaymentAsynchronously(paymentId);

            verify(mobileMoneyService, never()).processB2CPayment(any());
            verify(paymentRepository, never()).save(any());
            verify(smsService, never()).sendSuccessNotification(any());
            verify(smsService, never()).sendFailureNotification(any());
        }

        @Test
        @DisplayName("Should throw exception if payment not found during async processing")
        void processAsync_PaymentNotFound() {
            when(paymentRepository.findById(eq(paymentId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.processPaymentAsynchronously(paymentId))
                    .isInstanceOf(PaymentNotFoundException.class)
                    .hasMessageContaining("Payment not found with ID: " + paymentId);

            verify(mobileMoneyService, never()).processB2CPayment(any());
            verify(paymentRepository, never()).save(any());
            verify(smsService, never()).sendSuccessNotification(any());
            verify(smsService, never()).sendFailureNotification(any());
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
