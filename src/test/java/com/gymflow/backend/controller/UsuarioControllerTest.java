package com.gymflow.backend.controller;

import com.gymflow.backend.dto.UsuarioResponseDTO;
import com.gymflow.backend.dto.request.CambioRolRequest;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioControllerTest {

    @Mock
    private UsuarioService usuarioService;

    @InjectMocks
    private UsuarioController usuarioController;

    @Test
    void cambiarRol_delegaRolYDevuelve200() {
        CambioRolRequest request = new CambioRolRequest();
        request.setRol(Rol.ENTRENADOR);
        UsuarioResponseDTO esperado = UsuarioResponseDTO.builder()
                .id(7L)
                .rol(Rol.ENTRENADOR)
                .build();
        when(usuarioService.cambiarRol(7L, Rol.ENTRENADOR)).thenReturn(esperado);

        ResponseEntity<UsuarioResponseDTO> respuesta = usuarioController.cambiarRol(
                7L, request);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).isSameAs(esperado);
        verify(usuarioService).cambiarRol(7L, Rol.ENTRENADOR);
    }

    @Test
    void cambiarRol_tienePreAuthorizeSoloAdmin() throws NoSuchMethodException {
        Method method = UsuarioController.class.getMethod(
                "cambiarRol", Long.class, CambioRolRequest.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('ADMIN')");
    }
}
