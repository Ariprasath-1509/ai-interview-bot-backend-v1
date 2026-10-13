package com.benchreadiness.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Bounds come from {@code app.assessment.*} (env-overridable). The fallbacks are the values that were
     * hard-coded before fix 6.1, so behaviour is unchanged unless the env sets them.
     * When pool and queue are full, submission throws {@code TaskRejectedException} (default AbortPolicy);
     * {@code AiController#assessAsync} turns that into FAILED + 503.
     */
    @Bean(name = "assessmentExecutor")
    public Executor assessmentExecutor(@Value("${app.assessment.core-pool-size:5}") int corePoolSize,
                                       @Value("${app.assessment.thread-pool-size:10}") int maxPoolSize,
                                       @Value("${app.assessment.queue-capacity:50}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("assessment-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
