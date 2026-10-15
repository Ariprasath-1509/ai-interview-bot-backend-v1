package com.benchreadiness.auth.service;

import com.benchreadiness.auth.client.ComplianceServiceClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

class AuditServiceLoginFailureTest {

    @Test
    @SuppressWarnings("unchecked")
    void auditEntryCarriesReasonAndMaskedAddressOnly() {
        ComplianceServiceClient client = mock(ComplianceServiceClient.class);
        AuditService audit = new AuditService(client);

        audit.recordLoginFailure("abcdef@gmail.com", LoginFailureReason.WRONG_PASSWORD);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(client).recordAuditLog(captor.capture());
        Map<String, Object> entry = captor.getValue();
        assertEquals("LOGIN_FAILURE", entry.get("action"));
        String detail = (String) entry.get("detail");
        assertTrue(detail.contains("reason=WRONG_PASSWORD"));
        assertTrue(detail.contains("ab***@gmail.com"));
        assertFalse(detail.contains("abcdef"));
    }
}
