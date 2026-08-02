package com.gymflow.backend.controller;

import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class UsuarioRoleSecurityIntegrationTest {

    private static final String PASSWORD = "PasswordSegura2026!";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void clienteRecibe403YAdminPuedeCambiarRol() {
        Usuario admin = crearUsuario(Rol.ADMIN, true);
        Usuario cliente = crearUsuario(Rol.CLIENTE, true);
        Usuario objetivo = crearUsuario(Rol.CLIENTE, true);

        String clienteToken = iniciarSesion(cliente.getEmail());
        ResponseEntity<String> clienteResponse = cambiarRol(
                clienteToken, objetivo.getId(), "ENTRENADOR", String.class);

        assertThat(clienteResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(clienteResponse.getBody()).contains("No tienes permisos");

        String adminToken = iniciarSesion(admin.getEmail());
        ResponseEntity<Map> adminResponse = cambiarRol(
                adminToken, objetivo.getId(), "ENTRENADOR", Map.class);

        assertThat(adminResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adminResponse.getBody()).isNotNull();
        assertThat(adminResponse.getBody().get("rol")).isEqualTo("ENTRENADOR");
    }

    @Test
    void rolInvalidoRecibe400() {
        Usuario admin = crearUsuario(Rol.ADMIN, true);
        Usuario objetivo = crearUsuario(Rol.CLIENTE, true);
        String adminToken = iniciarSesion(admin.getEmail());

        ResponseEntity<String> response = cambiarRol(
                adminToken, objetivo.getId(), "NO_EXISTE", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private Usuario crearUsuario(Rol rol, boolean activo) {
        String email = "role-security-" + System.nanoTime() + "@gymflow.test";
        return usuarioRepository.save(Usuario.builder()
                .nombre("Usuario Security Test")
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .rol(rol)
                .activo(activo)
                .build());
    }

    @SuppressWarnings("unchecked")
    private String iniciarSesion(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"email\":\"%s\",\"password\":\"%s\"}"
                .formatted(email, PASSWORD);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/login", new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return (String) response.getBody().get("token");
    }

    private <T> ResponseEntity<T> cambiarRol(
            String token, Long id, String rol, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"rol\":\"%s\"}".formatted(rol);
        return restTemplate.exchange(
                "/api/usuarios/" + id + "/rol",
                HttpMethod.PATCH,
                new HttpEntity<>(body, headers),
                responseType);
    }
}
