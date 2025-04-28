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
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Service
@Validated
@Slf4j
@AllArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final MobileMoneyService mobileMoneyService;
    private final SmsService smsService;
    private final PaymentMapper paymentMapper;
    private final Executor asyncExecutor = Executors.newCachedThreadPool(); // TODO: Configure properly as a Spring bean

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
        newPayment.setStatus(PaymentStatus.PENDING);

        Payment savedPayment = paymentRepository.saveAndFlush(newPayment);
        log.debug("Saved initial payment record with ID: {} and status: {}", savedPayment.getId(), savedPayment.getStatus());

        savedPayment.setStatus(PaymentStatus.PROCESSING);
        Payment processingPayment = paymentRepository.save(savedPayment);
        log.info("Payment status updated to PROCESSING for ID: {}", processingPayment.getId());

        try {
            CompletableFuture<Payment> mnoFuture = mobileMoneyService.processB2CPayment(processingPayment);
            mnoFuture.handleAsync((updatedPayment, ex) -> {
                if (ex != null) {
                    log.error("MNO processing failed exceptionally for paymentId: {}. Cause: {}", processingPayment.getId(), ex.getMessage(), ex);
                    handleMnoProcessingFailure(processingPayment.getId(), "MNO communication error: " + ex.getMessage());
                } else if (updatedPayment != null) {
                    log.info("MNO processing completed for paymentId: {} with status: {}", updatedPayment.getId(), updatedPayment.getStatus());
                    handleMnoProcessingCompletion(updatedPayment);
                } else {
                    log.error("MNO processing returned null result for paymentId: {}", processingPayment.getId());
                    handleMnoProcessingFailure(processingPayment.getId(), "MNO service returned null result");
                }
                return null;
            }, asyncExecutor);

        } catch (Exception e) {
            log.error("Failed to initiate MNO processing for paymentId: {}. Rolling back status to FAILED.", processingPayment.getId(), e);
            processingPayment.setStatus(PaymentStatus.FAILED);
            processingPayment.setFailureReason("Failed to initiate MNO request: " + e.getMessage());
            paymentRepository.save(processingPayment);
            throw new MMOServiceException("Failed to submit payment to MNO: " + e.getMessage(), e);
        }

        log.info("Successfully initiated payment processing for transactionId: {}. Current status: {}",
                processingPayment.getTransactionId(), processingPayment.getStatus());
        return paymentMapper.toResponse(processingPayment);
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

    @Transactional
    protected void handleMnoProcessingCompletion(Payment updatedPayment) {
        Optional<Payment> paymentOpt = paymentRepository.findById(updatedPayment.getId());
        if (paymentOpt.isEmpty()) {
            log.error("Payment record not found for ID {} during MNO completion handling.", updatedPayment.getId());
            return;
        }
        Payment paymentToUpdate = paymentOpt.get();

        if (paymentToUpdate.getStatus() != PaymentStatus.PROCESSING) {
            log.warn("Attempted to update payment ID {} from MNO callback, but status was already {}. Ignoring update.",
                    paymentToUpdate.getId(), paymentToUpdate.getStatus());
            return;
        }

        paymentToUpdate.setStatus(updatedPayment.getStatus());
        paymentToUpdate.setMnoReference(updatedPayment.getMnoReference());
        paymentToUpdate.setFailureReason(updatedPayment.getFailureReason());

        Payment finalPayment = paymentRepository.save(paymentToUpdate);
        log.info("Final payment status updated to {} for ID: {}", finalPayment.getStatus(), finalPayment.getId());

        if (finalPayment.getStatus() == PaymentStatus.SUCCESSFUL) {
            smsService.sendSuccessNotification(finalPayment);
        } else if (finalPayment.getStatus() == PaymentStatus.FAILED) {
            smsService.sendFailureNotification(finalPayment);
        }
    }

    @Transactional
    protected void handleMnoProcessingFailure(UUID paymentId, String reason) {
        Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);
        if (paymentOpt.isEmpty()) {
            log.error("Payment record not found for ID {} during MNO failure handling.", paymentId);
            return;
        }
        Payment paymentToUpdate = paymentOpt.get();

        // Avoid overwriting a final state
        if (paymentToUpdate.getStatus() != PaymentStatus.PROCESSING) {
            log.warn("Attempted to mark payment ID {} as FAILED from MNO callback, but status was already {}. Ignoring update.",
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
