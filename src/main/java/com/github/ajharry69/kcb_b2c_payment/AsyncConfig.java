package com.github.ajharry69.kcb_b2c_payment;


import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {
    public static final String MNO_TASK_EXECUTOR = "mnoTaskExecutor";

    @Bean(name = MNO_TASK_EXECUTOR)
    public Executor taskExecutor() {
        log.info("Creating Async Task Executor for MNO processing");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("MnoAsync-");
        executor.initialize();
        return executor;
    }
}
