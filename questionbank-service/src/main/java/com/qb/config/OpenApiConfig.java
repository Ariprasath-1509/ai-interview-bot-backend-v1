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
                        new Server().url("http://localhost:8080").description("Local Development Server")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your Supabase JWT token")));
    }
}
