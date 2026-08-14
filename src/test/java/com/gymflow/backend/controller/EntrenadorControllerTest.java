package com.gymflow.backend.controller;

import com.gymflow.backend.dto.HistorialAcompanamientoDTO;
import com.gymflow.backend.dto.MiEntrenadorDTO;
import com.gymflow.backend.service.EntrenadorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntrenadorControllerTest {

    @Mock
    private EntrenadorService entrenadorService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private EntrenadorController entrenadorController;

    @Test
    void asignarme_devuelve201YUsaElEmailDelPrincipal() {
        when(authentication.getName()).thenReturn("ana@gymflow.test");
        doNothing().when(entrenadorService).asignarme("ana@gymflow.test", 2L);

        ResponseEntity<Void> respuesta = entrenadorController.asignarme(authentication, 2L);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(201);
        verify(entrenadorService).asignarme("ana@gymflow.test", 2L);
        verifyNoMoreInteractions(entrenadorService);
    }

    @Test
    void miEntrenador_sinAcompañante_devuelve204() {
        when(authentication.getName()).thenReturn("beto@gymflow.test");
        when(entrenadorService.miEntrenador("beto@gymflow.test")).thenReturn(Optional.empty());

        ResponseEntity<MiEntrenadorDTO> respuesta = entrenadorController.miEntrenador(authentication);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(204);
        verify(entrenadorService).miEntrenador("beto@gymflow.test");
        verifyNoMoreInteractions(entrenadorService);
    }

    @Test
    void miEntrenador_conAcompañante_devuelve200() {
        MiEntrenadorDTO dto = new MiEntrenadorDTO(1L, "Coach Ana", null);
        when(authentication.getName()).thenReturn("beto@gymflow.test");
        when(entrenadorService.miEntrenador("beto@gymflow.test")).thenReturn(Optional.of(dto));

        ResponseEntity<MiEntrenadorDTO> respuesta = entrenadorController.miEntrenador(authentication);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).isSameAs(dto);
    }

    @Test
    void miHistorial_usaElEmailDelPrincipal() {
        HistorialAcompanamientoDTO entrada = new HistorialAcompanamientoDTO(7L, "Coach Ana", true, null);
        when(authentication.getName()).thenReturn("beto@gymflow.test");
        when(entrenadorService.miHistorial("beto@gymflow.test")).thenReturn(List.of(entrada));

        List<HistorialAcompanamientoDTO> respuesta = entrenadorController.miHistorial(authentication);

        assertThat(respuesta).containsExactly(entrada);
        verify(entrenadorService).miHistorial("beto@gymflow.test");
        verifyNoMoreInteractions(entrenadorService);
    }
}
