package com.github.ajharry69.kcb_b2c_payment.notification;

import com.github.ajharry69.kcb_b2c_payment.payment.model.Payment;

public interface SmsService {
    void sendNotification(Payment payment, String message);
    void sendSuccessNotification(Payment payment);
    void sendFailureNotification(Payment payment);
}
