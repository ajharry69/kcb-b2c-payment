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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Service layer handling the business logic for B2C payments.
 */
@Service
@Validated // Ensures that method parameters annotated with @Valid are validated
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final MobileMoneyService mobileMoneyService;
    private final SmsService smsService;
    private final PaymentMapper paymentMapper;
    // Use a dedicated executor for handling async MNO responses
    private final Executor asyncExecutor = Executors.newCachedThreadPool(); // TODO: Configure properly as a Spring bean

    public PaymentService(PaymentRepository paymentRepository,
                          MobileMoneyService mobileMoneyService,
                          SmsService smsService,
                          PaymentMapper paymentMapper) {
        this.paymentRepository = paymentRepository;
        this.mobileMoneyService = mobileMoneyService;
        this.smsService = smsService;
        this.paymentMapper = paymentMapper;
    }

    /**
     * Initiates a new B2C payment transaction.
     * Handles idempotency check based on transactionId.
     * Saves the initial payment record and triggers asynchronous MNO processing.
     *
     * @param paymentRequest The validated payment request DTO.
     * @return A PaymentResponse DTO representing the initial state (PENDING).
     * @throws DuplicateTransactionException if the transactionId already exists.
     */
    @Transactional // Ensures the initial save and potential check happen atomically
    public PaymentResponse initiatePayment(@Valid PaymentRequest paymentRequest) {
        log.info("Initiating payment for transactionId: {}", paymentRequest.transactionId());

        // 1. Idempotency Check: Check if transaction ID already exists
        Optional<Payment> existingPayment = paymentRepository.findByTransactionId(paymentRequest.transactionId());
        if (existingPayment.isPresent()) {
            // If it exists and is PENDING/PROCESSING, throw duplicate. If completed, return current status.
            Payment current = existingPayment.get();
            if (current.getStatus() == PaymentStatus.PENDING || current.getStatus() == PaymentStatus.PROCESSING) {
                log.warn("Duplicate transaction attempt for existing PENDING/PROCESSING payment: {}", paymentRequest.transactionId());
                throw new DuplicateTransactionException(paymentRequest.transactionId());
            } else {
                // Return the final status of the already completed transaction
                log.info("Returning status for already completed transactionId: {}", paymentRequest.transactionId());
                return paymentMapper.toResponse(current);
            }
        }

        // 2. Map Request to Entity and set initial state
        Payment newPayment = paymentMapper.toEntity(paymentRequest);
        newPayment.setStatus(PaymentStatus.PENDING); // Start as PENDING

        // 3. Save Initial Payment Record (Status: PENDING)
        Payment savedPayment = paymentRepository.saveAndFlush(newPayment); // Save and flush to get ID and ensure visibility
        log.debug("Saved initial payment record with ID: {} and status: {}", savedPayment.getId(), savedPayment.getStatus());


        // 4. Trigger Asynchronous MNO Processing (Status: PROCESSING)
        // Update status immediately before calling async MNO service
        savedPayment.setStatus(PaymentStatus.PROCESSING);
        Payment processingPayment = paymentRepository.save(savedPayment); // Save PROCESSING state
        log.info("Payment status updated to PROCESSING for ID: {}", processingPayment.getId());


        try {
            CompletableFuture<Payment> mnoFuture = mobileMoneyService.processB2CPayment(processingPayment);

            // 5. Handle MNO Response Asynchronously (Non-blocking)
            mnoFuture.handleAsync((updatedPayment, ex) -> {
                if (ex != null) {
                    // Handle exceptions from the MNO service future itself (e.g., network error submitting request)
                    log.error("MNO processing failed exceptionally for paymentId: {}. Cause: {}", processingPayment.getId(), ex.getMessage(), ex);
                    handleMnoProcessingFailure(processingPayment.getId(), "MNO communication error: " + ex.getMessage());
                } else if (updatedPayment != null) {
                    // MNO service returned a result (SUCCESSFUL or FAILED)
                    log.info("MNO processing completed for paymentId: {} with status: {}", updatedPayment.getId(), updatedPayment.getStatus());
                    handleMnoProcessingCompletion(updatedPayment);
                } else {
                    // Should not happen if MNO service adheres to contract, but handle defensively
                    log.error("MNO processing returned null result for paymentId: {}", processingPayment.getId());
                    handleMnoProcessingFailure(processingPayment.getId(), "MNO service returned null result");
                }
                return null; // Return type of handleAsync consumer
            }, asyncExecutor); // Use dedicated executor

        } catch (Exception e) {
            // Catch synchronous exceptions during the *initiation* of the async call
            log.error("Failed to initiate MNO processing for paymentId: {}. Rolling back status to FAILED.", processingPayment.getId(), e);
            // Rollback status immediately if submission fails
            processingPayment.setStatus(PaymentStatus.FAILED);
            processingPayment.setFailureReason("Failed to initiate MNO request: " + e.getMessage());
            paymentRepository.save(processingPayment); // Save FAILED state
            // No SMS needed here usually, as it never reached MNO processing stage
            throw new MMOServiceException("Failed to submit payment to MNO: " + e.getMessage(), e);
        }


        // 6. Return Initial Response (Status: PENDING or PROCESSING)
        // Return the state *before* async processing completes. Client should poll or use callbacks.
        // It's safer to return the PROCESSING state as that's what we just saved before the async call.
        log.info("Successfully initiated payment processing for transactionId: {}. Current status: {}",
                processingPayment.getTransactionId(), processingPayment.getStatus());
        return paymentMapper.toResponse(processingPayment);
    }

    /**
     * Retrieves the status and details of a specific payment by its internal ID.
     *
     * @param paymentId The UUID of the payment.
     * @return A PaymentResponse DTO.
     * @throws PaymentNotFoundException if the payment ID does not exist.
     */
    public PaymentResponse getPaymentById(UUID paymentId) {
        log.debug("Fetching payment by ID: {}", paymentId);
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        log.info("Found payment ID: {} with status: {}", paymentId, payment.getStatus());
        return paymentMapper.toResponse(payment);
    }

    /**
     * Retrieves the status and details of a specific payment by its client-provided transaction ID.
     *
     * @param transactionId The client's transaction identifier.
     * @return A PaymentResponse DTO.
     * @throws PaymentNotFoundException if the transaction ID does not exist.
     */
    public PaymentResponse getPaymentByTransactionId(String transactionId) {
        log.debug("Fetching payment by transactionId: {}", transactionId);
        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new PaymentNotFoundException(transactionId));
        log.info("Found payment transactionId: {} (ID: {}) with status: {}", transactionId, payment.getId(), payment.getStatus());
        return paymentMapper.toResponse(payment);
    }


    // --- Private Helper Methods for Async Handling ---

    /**
     * Handles the completion (success or MNO-reported failure) of the MNO processing.
     * Updates the payment status in the database and triggers SMS notification.
     * This method runs within the async callback context.
     */
    @Transactional // Needs a new transaction for database updates
    protected void handleMnoProcessingCompletion(Payment updatedPayment) {
        // Re-fetch the payment by ID to ensure we're working with the managed entity
        Optional<Payment> paymentOpt = paymentRepository.findById(updatedPayment.getId());
        if (paymentOpt.isEmpty()) {
            log.error("Payment record not found for ID {} during MNO completion handling.", updatedPayment.getId());
            // Cannot update or send SMS if record is gone
            return;
        }
        Payment paymentToUpdate = paymentOpt.get();

        // Avoid overwriting a final state if somehow processed twice (defensive check)
        if (paymentToUpdate.getStatus() != PaymentStatus.PROCESSING) {
            log.warn("Attempted to update payment ID {} from MNO callback, but status was already {}. Ignoring update.",
                    paymentToUpdate.getId(), paymentToUpdate.getStatus());
            return;
        }


        // Update fields from the MNO result
        paymentToUpdate.setStatus(updatedPayment.getStatus());
        paymentToUpdate.setMnoReference(updatedPayment.getMnoReference());
        paymentToUpdate.setFailureReason(updatedPayment.getFailureReason());
        // updatedAt will be updated automatically by AuditingEntityListener

        Payment finalPayment = paymentRepository.save(paymentToUpdate);
        log.info("Final payment status updated to {} for ID: {}", finalPayment.getStatus(), finalPayment.getId());

        // Send SMS notification based on the final status
        if (finalPayment.getStatus() == PaymentStatus.SUCCESSFUL) {
            smsService.sendSuccessNotification(finalPayment);
        } else if (finalPayment.getStatus() == PaymentStatus.FAILED) {
            smsService.sendFailureNotification(finalPayment);
        }
    }

    /**
     * Handles failures occurring during the MNO processing (e.g., communication errors, timeouts).
     * Updates the payment status to FAILED in the database.
     * This method runs within the async callback context.
     */
    @Transactional // Needs a new transaction for database updates
    protected void handleMnoProcessingFailure(UUID paymentId, String reason) {
        // Re-fetch the payment by ID
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

        // Send failure SMS
        smsService.sendFailureNotification(finalPayment);
    }
}
