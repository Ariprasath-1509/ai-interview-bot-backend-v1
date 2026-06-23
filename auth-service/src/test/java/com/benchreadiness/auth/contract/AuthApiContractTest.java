package com.benchreadiness.auth.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AuthApiContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void contractDefinesPublicAuthEndpoints() throws Exception {
        JsonNode contract = loadContract();
        assertEquals("auth-service", contract.get("service").asText());
        assertTrue(contract.get("publicEndpoints").size() >= 4);
    }

    @Test
    void responseDtosExposeContractFields() throws Exception {
        JsonNode contract = loadContract();
        for (JsonNode endpoint : contract.get("publicEndpoints")) {
            String schema = endpoint.get("successSchema").asText();
            Set<String> required = new HashSet<>();
            endpoint.get("requiredResponseFields").forEach(field -> required.add(field.asText()));
            Set<String> dtoFields = dtoFieldNames(schema);
            assertTrue(dtoFields.containsAll(required),
                    () -> schema + " missing fields: " + missing(required, dtoFields));
        }
    }

    private JsonNode loadContract() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/contracts/auth-public-api.json")) {
            assertNotNull(in, "Contract file must be on test classpath");
            return objectMapper.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private Set<String> dtoFieldNames(String className) throws ClassNotFoundException {
        Class<?> clazz = Class.forName("com.benchreadiness.auth.dto." + className);
        Set<String> names = new HashSet<>();
        for (Field field : clazz.getDeclaredFields()) {
            names.add(field.getName());
        }
        return names;
    }

    private Set<String> missing(Set<String> required, Set<String> actual) {
        Set<String> missing = new HashSet<>(required);
        missing.removeAll(actual);
        return missing;
    }
}
