package com.qb.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI questionBankOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("QuestionBank API")
                        .description("API for managing interview questions, companies, sessions, and AI-powered question digestion")
                        .version("v0.0.1")
                        .contact(new Contact()
                                .name("QuestionBank Team")
                                .email("dev@qb.com")))
                .servers(List.of(
                        new Server().url("http://localhost:6002").description("API Gateway"),
                        new Server().url("http://localhost:6016").description("QuestionBank Service (direct)")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT access token from /auth/login")));
    }
}
