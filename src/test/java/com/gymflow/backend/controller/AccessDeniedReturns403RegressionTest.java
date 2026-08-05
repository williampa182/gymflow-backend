package com.gymflow.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.repository.UsuarioRepository;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de regresión para el fix del 2026-08-01 (ver
 * collab/aplicado/2026-08-01-fix-access-denied-403.md): antes del fix,
 * AccessDeniedException (que extiende RuntimeException) era atrapada por
 * GlobalExceptionHandler.handleGenericRuntime y mapeada a 500 "Error
 * interno inesperado" en vez de 403. Consecuencia real: un CLIENTE (o
 * cualquier usuario con rol degradado) que llamara a un endpoint de ADMIN
 * rompía el dashboard con un 500 — el frontend no distingue 403 de 500 y
 * mostraba error genérico de carga en lugar de un "sin permisos" limpio.
 *
 * Este test registra un usuario real (rol CLIENTE forzado), toma su token
 * y pega contra endpoints protegidos con @PreAuthorize("hasRole('ADMIN')")
 * a través del HTTP real, verificando que la respuesta sea siempre un 403
 * con el mensaje de permisos — nunca un 500.
 *
 * Nota Fase 2: con el bootstrap del primer admin activo (2026-08-02),
 * `asegurarAdminActivo()` garantiza que exista un ADMIN antes de registrar,
 * para que el registro de aquí nazca CLIENTE determinísticamente en
 * cualquier base (fresca o no) y el 403 se pruebe como tal.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(properties = "app.security.auth-rate-limit.max-per-minute=1000")
class AccessDeniedReturns403RegressionTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @SuppressWarnings("unchecked")
    private String registrarClienteYTomarToken() {
        asegurarAdminActivo();

        String email = "regression-access-denied-" + System.nanoTime() + "@gymflow.com";
        String payload = """
                {
                  "nombre": "Cliente Regresion",
                  "email": "%s",
                  "password": "PasswordSegura2026!"
                }
                """.formatted(email);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/register",
                new HttpEntity<>(payload, headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("rol")).isEqualTo("CLIENTE");

        String token = (String) response.getBody().get("token");
        assertThat(token).as("el token del registro debe venir en la respuesta para este test").isNotBlank();
        return token;
    }

    private ResponseEntity<String> pegarEndpointAdminConToken(String token, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return restTemplate.exchange(
                path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    void clienteAccedeAUsuarios_devuelve403ConMensajeDePermisosNo500() {
        String token = registrarClienteYTomarToken();
        ResponseEntity<String> response = pegarEndpointAdminConToken(token, "/api/usuarios");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("No tienes permisos");
    }

    @Test
    void clienteAccedeAEstadisticasAdmin_devuelve403ConMensajeDePermisosNo500() {
        String token = registrarClienteYTomarToken();
        ResponseEntity<String> response = pegarEndpointAdminConToken(
                token, "/api/dashboard/admin/estadisticas");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("No tienes permisos");
    }

    // El bootstrap del primer admin (2026-08-02) solo aplica cuando el sistema
    // NO tiene admins. Garantizamos que exista uno para que el registro de este
    // test nazca CLIENTE determinísticamente (no ADMIN por bootstrap).
    private void asegurarAdminActivo() {
        if (usuarioRepository.countByRolAndActivo(Rol.ADMIN, true) == 0) {
            usuarioRepository.save(Usuario.builder()
                    .nombre("Admin Bootstrap")
                    .email("bootstrap-admin-" + System.nanoTime() + "@gymflow.com")
                    .password(passwordEncoder.encode("PasswordSegura2026!"))
                    .rol(Rol.ADMIN)
                    .activo(true)
                    .build());
        }
    }
}
