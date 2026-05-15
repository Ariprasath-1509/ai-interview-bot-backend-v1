package com.qb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.util.TimeZone;

@SpringBootApplication(exclude = {
		org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration.class
})
@EnableDiscoveryClient
@EnableFeignClients
public class QuestionbankApiApplication {

	public static void main(String[] args) {
		// Force UTC timezone to avoid PostgreSQL timezone issues
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(QuestionbankApiApplication.class, args);
	}

}
