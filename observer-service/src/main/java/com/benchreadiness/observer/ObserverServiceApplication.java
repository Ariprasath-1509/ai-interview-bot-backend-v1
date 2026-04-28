package com.benchreadiness.observer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ObserverServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ObserverServiceApplication.class, args);
    }
}
