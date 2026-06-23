package com.benchreadiness.ops;

import com.benchreadiness.ops.observer.config.FeignTracingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableFeignClients(defaultConfiguration = FeignTracingConfig.class)
public class OpsServiceApplication {
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(OpsServiceApplication.class, args);
    }
}
