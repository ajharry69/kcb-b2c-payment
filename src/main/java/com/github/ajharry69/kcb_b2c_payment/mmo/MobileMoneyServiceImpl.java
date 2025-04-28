package com.github.ajharry69.kcb_b2c_payment.mmo;

import com.github.ajharry69.kcb_b2c_payment.payment.model.Payment;
import com.github.ajharry69.kcb_b2c_payment.payment.model.PaymentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;


@Service
@Slf4j
public class MobileMoneyServiceImpl implements MobileMoneyService {

    @Value("${mock.mno.success-rate:0.9}") // Default 90% success rate
    private double successRate;

    @Override
    public CompletableFuture<Payment> processB2CPayment(Payment payment) {
        log.info("MOCK MNO: Received payment request for transactionId: {}", payment.getTransactionId());

        // Simulate network delay and processing time
        long delayMillis = ThreadLocalRandom.current().nextLong(500, 3000); // 0.5 to 3 seconds delay

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("MOCK MNO: Simulating processing delay of {} ms for transactionId: {}", delayMillis, payment.getTransactionId());
                TimeUnit.MILLISECONDS.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("MOCK MNO: Simulation interrupted for transactionId: {}", payment.getTransactionId(), e);
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("Processing interrupted");
                return payment;
            }

            // Simulate success or failure based on successRate
            boolean success = ThreadLocalRandom.current().nextDouble(0, 1) < successRate;

            if (success) {
                log.info("MOCK MNO: Simulating SUCCESS for transactionId: {}", payment.getTransactionId());
                payment.setStatus(PaymentStatus.SUCCESSFUL);
                payment.setMnoReference("MOCK_MNO_" + UUID.randomUUID().toString().substring(0, 12)); // Generate mock reference
                payment.setFailureReason(null);
            } else {
                log.warn("MOCK MNO: Simulating FAILURE for transactionId: {}", payment.getTransactionId());
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(generateRandomFailureReason());
                payment.setMnoReference(null);
            }
            return payment;
        });
    }

    private String generateRandomFailureReason() {
        String[] reasons = {
                "Insufficient funds",
                "Recipient account invalid",
                "Transaction limit exceeded",
                "Temporary network error",
                "System unavailable",
                "Duplicate transaction"
        };
        return reasons[ThreadLocalRandom.current().nextInt(reasons.length)];
    }
}