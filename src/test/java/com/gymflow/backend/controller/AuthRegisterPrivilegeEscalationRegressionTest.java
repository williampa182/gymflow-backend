package com.gymflow.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
 * Test de regresión para el hallazgo más grave de todo el Security Deep
 * Dive (THREAT_MODEL.md §7.0): RegisterRequest exponía un campo `rol` sin
 * restricción server-side, permitiendo que cualquiera se registrara como
 * ADMIN con un solo POST sin autenticación previa.
 *
 * El fix actual (whitelist CLIENTE/ENTRENADOR en AuthService + fuerza
 * CLIENTE para cualquier otro valor) ya está cubierto indirectamente por
 * AuthServiceTest, pero ese es un test de unidad sobre el service, no
 * ejercita el binding real de JSON a través del controller. Este test manda
 * el payload crudo tal cual lo mandaría un atacante — incluyendo el campo
 * "rol":"ADMIN" — contra el endpoint HTTP real.
 *
 * Nota Fase 2: CON bootstrap del primer admin activo (2026-08-02), el test
 * necesita un ADMIN presente para que la regla "registro público nunca
 * auto-escala" se pruebe determinísticamente en cualquier base (fresca o
 * no). Por eso `asegurarAdminActivo()` seembra un admin antes de registrar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(properties = "app.security.auth-rate-limit.max-per-minute=1000")
class AuthRegisterPrivilegeEscalationRegressionTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @SuppressWarnings("unchecked")
    @Test
    void registroConRolAdminEnElBody_ignoraElCampoYQuedaComoCliente() {
        asegurarAdminActivo();

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

    // El bootstrap del primer admin (2026-08-02) solo aplica cuando el sistema
    // NO tiene admins. Garantizamos que exista uno para que este test verifique
    // exclusivamente el "no auto-escala", no el bootstrap.
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
