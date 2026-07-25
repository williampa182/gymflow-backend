package com.gymflow.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de regresión para el hallazgo más grave de todo el Security Deep
 * Dive (THREAT_MODEL.md §7.0): RegisterRequest exponía un campo `rol` sin
 * restricción server-side, permitiendo que cualquiera se registrara como
 * ADMIN con un solo POST sin autenticación previa.
 *
 * El fix actual (RegisterRequest sin campo `rol` + AuthService fuerza
 * Rol.CLIENTE) ya está cubierto indirectamente por AuthServiceTest, pero
 * ese es un test de unidad sobre el service, no ejercita el binding real
 * de JSON a través del controller. Este test manda el payload crudo tal
 * cual lo mandaría un atacante — incluyendo el campo "rol" que ya no
 * debería existir en el DTO — contra el endpoint HTTP real, para que
 * cualquier reintroducción futura del campo (con o sin el guard en
 * AuthService) se detecte acá.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AuthRegisterPrivilegeEscalationRegressionTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @SuppressWarnings("unchecked")
    @Test
    void registroConRolAdminEnElBody_ignoraElCampoYQuedaComoCliente() {
        String email = "regression-priv-esc-" + System.nanoTime() + "@gymflow.com";

        String payloadConRolAdmin = """
                {
                  "nombre": "Atacante Regresion",
                  "email": "%s",
                  "password": "PasswordSegura2026!",
                  "rol": "ADMIN"
                }
                """.formatted(email);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/register",
                new HttpEntity<>(payloadConRolAdmin, headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("rol"))
                .as("un registro público nunca debe poder auto-asignarse un rol distinto a CLIENTE")
                .isEqualTo("CLIENTE");
    }
}
