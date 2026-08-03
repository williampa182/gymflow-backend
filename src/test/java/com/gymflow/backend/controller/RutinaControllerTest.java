package com.gymflow.backend.controller;

import com.gymflow.backend.dto.EjercicioRequestDTO;
import com.gymflow.backend.dto.RutinaRequestDTO;
import com.gymflow.backend.dto.RutinaResponseDTO;
import com.gymflow.backend.service.RutinaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RutinaControllerTest {

    @Mock
    private RutinaService rutinaService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private RutinaController rutinaController;

    @Test
    void listarMias_usaElEmailDelPrincipal() {
        when(authentication.getName()).thenReturn("ana@gymflow.test");
        when(rutinaService.listarMias("ana@gymflow.test")).thenReturn(List.of());

        List<RutinaResponseDTO> respuesta = rutinaController.listarMias(authentication);

        assertThat(respuesta).isEmpty();
        verify(rutinaService).listarMias("ana@gymflow.test");
        verifyNoMoreInteractions(rutinaService);
    }

    @Test
    void listarAsignadas_usaElEmailDelPrincipal() {
        when(authentication.getName()).thenReturn("beto@gymflow.test");
        when(rutinaService.listarAsignadas("beto@gymflow.test")).thenReturn(List.of());

        List<RutinaResponseDTO> respuesta = rutinaController.listarAsignadas(authentication);

        assertThat(respuesta).isEmpty();
        verify(rutinaService).listarAsignadas("beto@gymflow.test");
        verifyNoMoreInteractions(rutinaService);
    }

    @Test
    void crear_devuelve201YDelega() {
        RutinaRequestDTO request = new RutinaRequestDTO("Full Body", null,
                List.of(new EjercicioRequestDTO(null, "Press banca", 3, 10)));
        RutinaResponseDTO creada = RutinaResponseDTO.from(
                com.gymflow.backend.model.Rutina.builder().id(10L).nombre("Full Body").build());
        when(authentication.getName()).thenReturn("ana@gymflow.test");
        when(rutinaService.crear("ana@gymflow.test", request)).thenReturn(creada);

        ResponseEntity<RutinaResponseDTO> respuesta = rutinaController.crear(authentication, request);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(201);
        assertThat(respuesta.getBody()).isSameAs(creada);
        verify(rutinaService).crear("ana@gymflow.test", request);
        verifyNoMoreInteractions(rutinaService);
    }

    @Test
    void asignar_devuelve204YDelegaConElEmailDelPrincipal() {
        when(authentication.getName()).thenReturn("ana@gymflow.test");
        doNothing().when(rutinaService).asignar("ana@gymflow.test", 10L, 2L);

        ResponseEntity<Void> respuesta = rutinaController.asignar(authentication, 10L, 2L);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(204);
        verify(rutinaService).asignar("ana@gymflow.test", 10L, 2L);
        verifyNoMoreInteractions(rutinaService);
    }
}
