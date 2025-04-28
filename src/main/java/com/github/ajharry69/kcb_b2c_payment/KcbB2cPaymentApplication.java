package com.github.ajharry69.kcb_b2c_payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class KcbB2cPaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(KcbB2cPaymentApplication.class, args);
    }

}
