package com.github.ajharry69.kcb_b2c_payment.notification;

import com.github.ajharry69.kcb_b2c_payment.payment.model.Payment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SmsServiceImpl implements SmsService {
    @Override
    public void sendNotification(Payment payment, String message) {
        log.info("MOCK SMS: Sending notification to {}: {}",
                payment.getRecipientPhoneNumber(), message);
    }

    @Override
    public void sendSuccessNotification(Payment payment) {
        String message = String.format(
                "Dear Customer, you have received %s %s. Transaction Ref: %s.",
                payment.getCurrency(),
                payment.getAmount(),
                payment.getMnoReference() != null ? payment.getMnoReference() : payment.getTransactionId()
        );
        sendNotification(payment, message);
    }

    @Override
    public void sendFailureNotification(Payment payment) {
        String reason = payment.getFailureReason() != null ? payment.getFailureReason() : "an unknown issue";
        String message = String.format(
                "Dear Customer, the payment of %s %s failed due to: %s. Transaction ID: %s.",
                payment.getCurrency(),
                payment.getAmount(),
                reason,
                payment.getTransactionId()
        );
        sendNotification(payment, message);
    }
}
