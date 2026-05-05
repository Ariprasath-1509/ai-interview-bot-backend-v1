package com.benchreadiness.interview.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Map;
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
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.initialize();
        return executor;
    }

    private static class ContextPropagatingTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            // Capture headers from the current thread (servlet thread)
            Map<String, String> headers = FeignAuthInterceptor.captureFromRequest();
            return () -> {
                try {
                    // Set headers in the async thread
                    if (headers != null) {
                        FeignAuthInterceptor.setHeaders(headers);
                    }
                    runnable.run();
                } finally {
                    // Clean up ThreadLocal to prevent memory leaks
                    FeignAuthInterceptor.clear();
                }
            };
        }
    }
}