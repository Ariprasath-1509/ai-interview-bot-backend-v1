package com.benchreadiness.screening;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableRetry
@EnableScheduling
@EnableFeignClients
public class ScreeningServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScreeningServiceApplication.class, args);
    }
}
