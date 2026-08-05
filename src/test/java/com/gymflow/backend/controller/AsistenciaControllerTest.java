package com.gymflow.backend.controller;

import com.gymflow.backend.dto.AdminMarcarAsistenciaRequestDTO;
import com.gymflow.backend.dto.AsistenciaAcompanadoDTO;
import com.gymflow.backend.dto.AsistenciaResponseDTO;
import com.gymflow.backend.dto.AsistenciaSemanaDTO;
import com.gymflow.backend.dto.CarnetResponseDTO;
import com.gymflow.backend.dto.KioskCheckInRequestDTO;
import com.gymflow.backend.model.enums.MetodoAsistencia;
import com.gymflow.backend.service.AsistenciaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsistenciaControllerTest {

    @Mock
    private AsistenciaService asistenciaService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AsistenciaController asistenciaController;

    @Test
    void marcarMi_devuelve201YUsaElEmailDelPrincipal() {
        AsistenciaResponseDTO dto = AsistenciaResponseDTO.builder()
                .id(7L)
                .usuarioId(1L)
                .nombre("Ana")
                .fecha(LocalDate.of(2026, 8, 3))
                .entradaEn(LocalDateTime.of(2026, 8, 3, 10, 0))
                .metodo(MetodoAsistencia.SELF)
                .build();
        when(authentication.getName()).thenReturn("ana@gymflow.test");
        when(asistenciaService.marcarMi("ana@gymflow.test")).thenReturn(dto);

        ResponseEntity<AsistenciaResponseDTO> respuesta = asistenciaController.marcarMi(authentication);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(201);
        assertThat(respuesta.getBody()).isSameAs(dto);
        verify(asistenciaService).marcarMi("ana@gymflow.test");
        verifyNoMoreInteractions(asistenciaService);
    }

    @Test
    void semana_devuelve200YUsaElEmailDelPrincipal() {
        AsistenciaSemanaDTO dto = AsistenciaSemanaDTO.builder()
                .fechaDesde(LocalDate.of(2026, 8, 3))
                .fechaHasta(LocalDate.of(2026, 8, 9))
                .total(0)
                .asistencias(List.of())
                .build();
        when(authentication.getName()).thenReturn("ana@gymflow.test");
        when(asistenciaService.semana("ana@gymflow.test")).thenReturn(dto);

        ResponseEntity<AsistenciaSemanaDTO> respuesta = asistenciaController.semana(authentication);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).isSameAs(dto);
        verify(asistenciaService).semana("ana@gymflow.test");
        verifyNoMoreInteractions(asistenciaService);
    }

    @Test
    void miCarnet_devuelve200_yUsaElEmailDelPrincipal() {
        CarnetResponseDTO dto = CarnetResponseDTO.builder()
                .codigoCarnet("ABC123")
                .build();
        when(authentication.getName()).thenReturn("ana@gymflow.test");
        when(asistenciaService.miCarnet("ana@gymflow.test")).thenReturn(dto);

        ResponseEntity<CarnetResponseDTO> respuesta = asistenciaController.miCarnet(authentication);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).isSameAs(dto);
        verify(asistenciaService).miCarnet("ana@gymflow.test");
    }

    @Test
    void miCarnet_tienePreAuthorizeSoloCliente() throws NoSuchMethodException {
        Method method = AsistenciaController.class.getMethod("miCarnet", Authentication.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('CLIENTE')");
    }

    // ---- Fase 5, P4: kiosco (POST /api/asistencias/kiosk) ----

    @Test
    void marcarKiosk_devuelve201YUsaElCodigoDelBodyYLaKeyDelHeader() {
        KioskCheckInRequestDTO request = new KioskCheckInRequestDTO();
        request.setCodigo("ABC123");
        AsistenciaResponseDTO dto = AsistenciaResponseDTO.builder()
                .id(9L)
                .usuarioId(1L)
                .nombre("Ana")
                .fecha(LocalDate.of(2026, 8, 3))
                .entradaEn(LocalDateTime.of(2026, 8, 3, 10, 0))
                .metodo(MetodoAsistencia.KIOSK_CARNET)
                .build();
        when(asistenciaService.marcarKiosk("ABC123", "key-del-dispositivo")).thenReturn(dto);

        ResponseEntity<AsistenciaResponseDTO> respuesta =
                asistenciaController.marcarKiosk(request, "key-del-dispositivo");

        assertThat(respuesta.getStatusCode().value()).isEqualTo(201);
        assertThat(respuesta.getBody()).isSameAs(dto);
        verify(asistenciaService).marcarKiosk("ABC123", "key-del-dispositivo");
        verifyNoMoreInteractions(asistenciaService);
    }

    @Test
    void marcarKiosk_sinPreAuthorize_porQueLaCredencialEsDelDispositivo() throws NoSuchMethodException {
        // Es la ÚNICA excepción al "todo autenticado": permitAll() en
        // SecurityConfig + X-Kiosk-Key validada en el controller + filtro de
        // rate limit. La persona que escanea no se autentica con JWT.
        Method method = AsistenciaController.class.getMethod(
                "marcarKiosk",
                KioskCheckInRequestDTO.class, String.class);

        assertThat(method.getAnnotation(PreAuthorize.class)).isNull();
    }

    // ---- Fase 5, P5: semana del ENTRENADOR + control del ADMIN ----

    @Test
    void semanaAcompanados_devuelve200YUsaElEmailDelEntrenador() {
        AsistenciaAcompanadoDTO dto = AsistenciaAcompanadoDTO.builder()
                .clienteId(1L)
                .clienteNombre("Ana")
                .asistencias(List.of())
                .build();
        when(authentication.getName()).thenReturn("entrenador@gymflow.test");
        when(asistenciaService.semanaAcompanados("entrenador@gymflow.test")).thenReturn(List.of(dto));

        ResponseEntity<List<AsistenciaAcompanadoDTO>> respuesta =
                asistenciaController.semanaAcompanados(authentication);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).containsExactly(dto);
        verify(asistenciaService).semanaAcompanados("entrenador@gymflow.test");
        verifyNoMoreInteractions(asistenciaService);
    }

    @Test
    void adminMarcar_devuelve201YDelegaElUsuarioId() {
        AdminMarcarAsistenciaRequestDTO request = new AdminMarcarAsistenciaRequestDTO();
        request.setUsuarioId(1L);
        AsistenciaResponseDTO dto = AsistenciaResponseDTO.builder()
                .id(8L)
                .usuarioId(1L)
                .nombre("Ana")
                .fecha(LocalDate.of(2026, 8, 3))
                .entradaEn(LocalDateTime.of(2026, 8, 3, 10, 0))
                .metodo(MetodoAsistencia.ADMIN)
                .build();
        when(asistenciaService.adminMarcar(1L)).thenReturn(dto);

        ResponseEntity<AsistenciaResponseDTO> respuesta = asistenciaController.adminMarcar(request);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(201);
        assertThat(respuesta.getBody()).isSameAs(dto);
        verify(asistenciaService).adminMarcar(1L);
    }

    @Test
    void desmarcar_devuelve204YDelegaElId() {
        ResponseEntity<Void> respuesta = asistenciaController.desmarcar(7L);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(204);
        verify(asistenciaService).desmarcar(7L);
    }

    @Test
    void historial_devuelve200YDelegaUsuarioIdYPageable() {
        Page<AsistenciaResponseDTO> pagina = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(asistenciaService.historial(1L, PageRequest.of(0, 20))).thenReturn(pagina);

        ResponseEntity<Page<AsistenciaResponseDTO>> respuesta =
                asistenciaController.historial(1L, PageRequest.of(0, 20));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).isSameAs(pagina);
        verify(asistenciaService).historial(1L, PageRequest.of(0, 20));
    }

    @Test
    void preAuthorize_deLosEndpointsP5() throws NoSuchMethodException {
        assertThat(AsistenciaController.class.getMethod("semanaAcompanados", Authentication.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ENTRENADOR')");
        assertThat(AsistenciaController.class.getMethod("adminMarcar", AdminMarcarAsistenciaRequestDTO.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
        assertThat(AsistenciaController.class.getMethod("desmarcar", Long.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
        assertThat(AsistenciaController.class.getMethod("historial", Long.class, Pageable.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
    }
}
