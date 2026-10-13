package com.benchreadiness.interview.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean(name = "resumeProcessingExecutor")
    public Executor resumeProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ResumeProcessor-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "bulkInterviewExecutor")
    public Executor bulkInterviewExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Fix 6.14: core = max = 20 (the bulk batch limit). With core 5 the pool only grows once the
        // queue (50) is full, so a 20-candidate batch ran 5 at a time and overran the bulk wait.
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("BulkInterview-");
        executor.initialize();
        return executor;
    }
}