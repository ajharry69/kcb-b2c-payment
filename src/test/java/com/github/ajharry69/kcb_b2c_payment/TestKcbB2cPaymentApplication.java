package com.github.ajharry69.kcb_b2c_payment;

import org.springframework.boot.SpringApplication;

public class TestKcbB2cPaymentApplication {

    public static void main(String[] args) {
        SpringApplication.from(KcbB2cPaymentApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
