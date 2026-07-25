package com.gymflow.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@TestPropertySource(properties = "app.debug-headers.enabled=true")
class DiagnosticHeaderControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testDebugHeadersEndpoint() {
        // El flag se activa explicitamente en este test via @TestPropertySource,
        // no depende de un application-test.yml (que no existe en este proyecto).
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/v1/debug/headers", Map.class);
        assertEquals(200, response.getStatusCode().value());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.containsKey("remoteAddr"));
        assertTrue(body.containsKey("remoteHost"));
        assertTrue(body.containsKey("remotePort"));
        assertTrue(body.containsKey("headers"));
        
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) body.get("headers");
        assertTrue(headers.containsKey("host"));
    }
}
