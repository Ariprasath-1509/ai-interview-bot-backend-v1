package com.benchreadiness.auth.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiConfigTest {

    @Test
    void openApiDeclaresBearerSecurity() {
        OpenApiConfig config = new OpenApiConfig();
        var openApi = config.authServiceOpenAPI();

        assertEquals("BenchReadiness Auth API", openApi.getInfo().getTitle());
        assertNotNull(openApi.getComponents().getSecuritySchemes().get("Bearer Authentication"));
        assertFalse(openApi.getServers().isEmpty());
    }
}
