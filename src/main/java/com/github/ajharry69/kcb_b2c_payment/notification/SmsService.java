package com.github.ajharry69.kcb_b2c_payment.notification;

import com.github.ajharry69.kcb_b2c_payment.payment.model.Payment;

/**
 * Abstraction for sending SMS notifications.
 */
public interface SmsService {

    /**
     * Sends an SMS notification regarding a payment transaction.
     * This can be fire-and-forget or return a status depending on requirements.
     *
     * @param payment The payment details used to construct the notification message.
     * @param message The specific message content to send.
     */
    void sendNotification(Payment payment, String message);

    /**
     * Sends a notification specifically for a successful payment.
     *
     * @param payment The successful payment details.
     */
    void sendSuccessNotification(Payment payment);

    /**
     * Sends a notification specifically for a failed payment.
     *
     * @param payment The failed payment details.
     */
    void sendFailureNotification(Payment payment);
}
