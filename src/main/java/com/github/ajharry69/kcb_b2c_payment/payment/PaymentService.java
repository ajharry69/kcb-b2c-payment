package com.github.ajharry69.kcb_b2c_payment.payment;

import com.github.ajharry69.kcb_b2c_payment.AsyncConfig;
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
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final MobileMoneyService mobileMoneyService;
    private final SmsService smsService;
    private final PaymentMapper paymentMapper;

    @Transactional
    public PaymentResponse initiatePayment(@Valid PaymentRequest paymentRequest) {
        log.info("Initiating payment for transactionId: {}", paymentRequest.transactionId());

        Optional<Payment> existingPayment = paymentRepository.findByTransactionId(paymentRequest.transactionId());
        if (existingPayment.isPresent()) {
            Payment current = existingPayment.get();
            if (current.getStatus() == PaymentStatus.PENDING || current.getStatus() == PaymentStatus.PROCESSING) {
                log.warn("Duplicate transaction attempt for existing PENDING/PROCESSING payment: {}", paymentRequest.transactionId());
                throw new DuplicateTransactionException(paymentRequest.transactionId());
            } else {
                log.info("Returning status for already completed transactionId: {}", paymentRequest.transactionId());
                return paymentMapper.toResponse(current);
            }
        }

        Payment newPayment = paymentMapper.toEntity(paymentRequest);

        Payment savedPayment = paymentRepository.saveAndFlush(newPayment);
        log.debug("Saved initial payment record with ID: {} and status: {}", savedPayment.getId(), savedPayment.getStatus());

        savedPayment.setStatus(PaymentStatus.PROCESSING);
        Payment processingPayment = paymentRepository.save(savedPayment);
        log.info("Payment status updated to PROCESSING for ID: {}", processingPayment.getId());

        try {
            processPaymentAsynchronously(processingPayment.getId());
            log.info("Successfully triggered async MNO processing for payment ID: {}", processingPayment.getId());
        } catch (Exception e) {
            log.error("Failed to submit MNO processing task for paymentId: {}. Rolling back status to FAILED.", processingPayment.getId(), e);
            processingPayment.setStatus(PaymentStatus.FAILED);
            processingPayment.setFailureReason("Failed to initiate async MNO task: " + e.getMessage());
            paymentRepository.save(processingPayment);
            throw new MMOServiceException("Failed to submit payment processing task: " + e.getMessage(), e);
        }

        log.info("Successfully initiated payment processing for transactionId: {}. Current status: {}",
                processingPayment.getTransactionId(), processingPayment.getStatus());
        return paymentMapper.toResponse(processingPayment);
    }

    @Async(AsyncConfig.MNO_TASK_EXECUTOR)
    @Transactional
    public void processPaymentAsynchronously(UUID paymentId) {
        log.info("Starting async MNO processing for payment ID: {} [Thread: {}]", paymentId, Thread.currentThread().getName());

        Payment paymentToProcess = paymentRepository.findById(paymentId)
                .orElseThrow(() -> {
                    // This case should be rare if called correctly after initial save
                    log.error("Payment record not found for ID {} in async MNO processing task.", paymentId);
                    return new PaymentNotFoundException(paymentId);
                });

        if (paymentToProcess.getStatus() != PaymentStatus.PROCESSING) {
            log.warn("Async MNO processing for payment ID {} skipped: Status is already {}.", paymentId, paymentToProcess.getStatus());
            return;
        }

        try {
            CompletableFuture<Payment> mnoFuture = mobileMoneyService.processB2CPayment(paymentToProcess);
            Payment updatedPaymentResult = mnoFuture.join();

            log.info(
                    "MNO processing completed for paymentId: {} with status: {}",
                    updatedPaymentResult.getId(),
                    updatedPaymentResult.getStatus());
            handleMnoProcessingCompletion(updatedPaymentResult);
        } catch (Exception ex) {
            log.error(
                    "MNO processing failed exceptionally during async task for paymentId: {}. Cause: {}",
                    paymentId,
                    ex.getMessage(),
                    ex);
            handleMnoProcessingFailure(paymentId, "MNO communication error: " + ex.getMessage());
        }
    }

    public PaymentResponse getPaymentById(UUID paymentId) {
        log.debug("Fetching payment by ID: {}", paymentId);
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        log.info("Found payment ID: {} with status: {}", paymentId, payment.getStatus());
        return paymentMapper.toResponse(payment);
    }

    public PaymentResponse getPaymentByTransactionId(String transactionId) {
        log.debug("Fetching payment by transactionId: {}", transactionId);
        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new PaymentNotFoundException(transactionId));
        log.info("Found payment transactionId: {} (ID: {}) with status: {}", transactionId, payment.getId(), payment.getStatus());
        return paymentMapper.toResponse(payment);
    }

    protected void handleMnoProcessingCompletion(Payment payment) {
        Payment paymentToUpdate = paymentRepository.findById(payment.getId())
                .orElse(null);

        if (paymentToUpdate == null) {
            log.error("Payment record not found for ID {} during MNO completion handling.", payment.getId());
            return;
        }

        if (paymentToUpdate.getStatus() != PaymentStatus.PROCESSING) {
            log.warn("Attempted to update payment ID {} from MNO completion, but status was already {}. Ignoring update.",
                    paymentToUpdate.getId(), paymentToUpdate.getStatus());
            return;
        }

        paymentToUpdate.setStatus(payment.getStatus());
        paymentToUpdate.setMnoReference(payment.getMnoReference());
        paymentToUpdate.setFailureReason(payment.getFailureReason());

        Payment finalPayment = paymentRepository.save(paymentToUpdate);
        log.info("Final payment status updated to {} for ID: {}", finalPayment.getStatus(), finalPayment.getId());

        if (finalPayment.getStatus() == PaymentStatus.SUCCESSFUL) {
            smsService.sendSuccessNotification(finalPayment);
        } else if (finalPayment.getStatus() == PaymentStatus.FAILED) {
            smsService.sendFailureNotification(finalPayment);
        }
    }

    protected void handleMnoProcessingFailure(UUID paymentId, String reason) {
        Payment paymentToUpdate = paymentRepository.findById(paymentId)
                .orElse(null);

        if (paymentToUpdate == null) {
            log.error("Payment record not found for ID {} during MNO failure handling.", paymentId);
            return;
        }

        if (paymentToUpdate.getStatus() != PaymentStatus.PROCESSING) {
            log.warn("Attempted to flag payment ID {} as FAILED from MNO failure handler, but status was already {}. Ignoring update.",
                    paymentToUpdate.getId(), paymentToUpdate.getStatus());
            return;
        }

        paymentToUpdate.setStatus(PaymentStatus.FAILED);
        paymentToUpdate.setFailureReason(reason);
        Payment finalPayment = paymentRepository.save(paymentToUpdate);
        log.info("Payment status updated to FAILED due to processing error for ID: {}", finalPayment.getId());
        smsService.sendFailureNotification(finalPayment);
    }
}
