package com.github.ajharry69.kcb_b2c_payment.mmo;

import com.github.ajharry69.kcb_b2c_payment.payment.model.Payment;

import java.util.concurrent.CompletableFuture;


public interface MobileMoneyService {

    /**
     * Initiates a B2C payment request to the MNO.
     * This method should be asynchronous as MNO processing can take time.
     *
     * @param payment The payment details to be processed.
     * @return A CompletableFuture representing the eventual result of the MNO transaction.
     * The future completes with an updated Payment object (with status, mnoReference, or failureReason)
     * or completes exceptionally if the initial request submission fails catastrophically.
     */
    CompletableFuture<Payment> processB2CPayment(Payment payment);
}