package com.github.ajharry69.kcb_b2c_payment.payment;

import com.github.ajharry69.kcb_b2c_payment.payment.dto.PaymentRequest;
import com.github.ajharry69.kcb_b2c_payment.payment.dto.PaymentResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@AllArgsConstructor
@Slf4j
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_payment.initiate')")
    public ResponseEntity<PaymentResponse> initiatePayment(@Valid @RequestBody PaymentRequest paymentRequest) {
        log.info("Received payment initiation request for transactionId: {}", paymentRequest.transactionId());
        PaymentResponse response = paymentService.initiatePayment(paymentRequest);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.paymentId())
                .toUri();

        // Return 202 Accepted since the process is asynchronous
        return ResponseEntity.accepted().location(location).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_payment.read')")
    public ResponseEntity<PaymentResponse> getPaymentById(@PathVariable UUID id) {
        log.info("Received request to get payment by ID: {}", id);
        PaymentResponse response = paymentService.getPaymentById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping(params = "transactionId")
    @PreAuthorize("hasAuthority('SCOPE_payment.read')")
    public ResponseEntity<PaymentResponse> getPaymentByTransactionId(@RequestParam String transactionId) {
        log.info("Received request to get payment by transactionId: {}", transactionId);
        PaymentResponse response = paymentService.getPaymentByTransactionId(transactionId);
        return ResponseEntity.ok(response);
    }
}
