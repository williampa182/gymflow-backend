package com.gymflow.backend.controller;

import com.gymflow.backend.dto.dashboard.AsistenciasSemanaStatsDTO;
import com.gymflow.backend.service.DashboardAdminService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardAdminControllerTest {

    @Mock
    private DashboardAdminService dashboardAdminService;

    @InjectMocks
    private DashboardAdminController dashboardAdminController;

    @Test
    void obtenerAsistenciasSemana_devuelve200YDelega() {
        AsistenciasSemanaStatsDTO dto = new AsistenciasSemanaStatsDTO(3,
                List.of(new AsistenciasSemanaStatsDTO.AsistenciaDiaStat(LocalDate.of(2026, 8, 3), 3)));
        when(dashboardAdminService.obtenerAsistenciasSemana()).thenReturn(dto);

        ResponseEntity<AsistenciasSemanaStatsDTO> respuesta =
                dashboardAdminController.obtenerAsistenciasSemana();

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).isSameAs(dto);
        verify(dashboardAdminService).obtenerAsistenciasSemana();
    }

    @Test
    void obtenerAsistenciasSemana_tienePreAuthorizeSoloAdmin() throws NoSuchMethodException {
        Method method = DashboardAdminController.class.getMethod("obtenerAsistenciasSemana");

        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
    }
}
