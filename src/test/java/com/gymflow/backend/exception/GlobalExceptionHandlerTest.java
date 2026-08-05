package com.gymflow.backend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void duplicadoDeAsistencia_mapeaA409() {
        ResponseEntity<Map<String, Object>> respuesta = handler.handleGenericRuntime(
                new RuntimeException("Ya registraste tu entrada hoy"));
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void usuarioDadoDeBaja_mapeaA403() {
        ResponseEntity<Map<String, Object>> respuesta = handler.handleGenericRuntime(
                new RuntimeException("El usuario está dado de baja"));
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void sinPlanActivo_mapeaA400() {
        ResponseEntity<Map<String, Object>> respuesta = handler.handleGenericRuntime(
                new RuntimeException("No tenés un plan activo para registrar tu entrada"));
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}