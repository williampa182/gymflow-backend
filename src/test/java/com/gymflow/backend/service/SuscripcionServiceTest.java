package com.gymflow.backend.service;

import com.gymflow.backend.dto.SuscripcionRequestDTO;
import com.gymflow.backend.dto.SuscripcionResponseDTO;
import com.gymflow.backend.model.Plan;
import com.gymflow.backend.model.Suscripcion;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.EstadoSuscripcion;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.model.enums.TipoPlan;
import com.gymflow.backend.repository.PlanRepository;
import com.gymflow.backend.repository.SuscripcionRepository;
import com.gymflow.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuscripcionServiceTest {

    @Mock
    private SuscripcionRepository suscripcionRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PlanRepository planRepository;

    @InjectMocks
    private SuscripcionService suscripcionService;

    private Usuario usuario;
    private Plan plan;
    private Suscripcion suscripcion;
    private SuscripcionRequestDTO request;

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder()
                .id(1L)
                .nombre("William Admin")
                .email("william@gymflow.com")
                .password("hashed")
                .rol(Rol.ADMIN)
                .activo(true)
                .build();

        plan = Plan.builder()
                .id(1L)
                .nombre("Plan Mensual")
                .precio(new BigDecimal("50000"))
                .duracionDias(30)
                .tipo(TipoPlan.MENSUAL)
                .activo(true)
                .build();

        suscripcion = Suscripcion.builder()
                .id(1L)
                .usuario(usuario)
                .plan(plan)
                .fechaInicio(LocalDate.of(2026, 7, 1))
                .fechaFin(LocalDate.of(2026, 7, 31))
                .estado(EstadoSuscripcion.ACTIVA)
                .build();

        request = new SuscripcionRequestDTO();
        request.setUsuarioId(1L);
        request.setPlanId(1L);
        request.setFechaInicio(LocalDate.of(2026, 7, 1));
    }

    @Test
    @SuppressWarnings("null")
    void crear_exitoso() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(suscripcionRepository.findByUsuarioIdAndEstado(1L, EstadoSuscripcion.ACTIVA))
                .thenReturn(Optional.empty());

        SuscripcionResponseDTO response = suscripcionService.crear(request);

        assertThat(response.getNombreUsuario()).isEqualTo("William Admin");
        assertThat(response.getNombrePlan()).isEqualTo("Plan Mensual");
        assertThat(response.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(response.getFechaFin()).isEqualTo(LocalDate.of(2026, 7, 31));
        verify(suscripcionRepository).save(any());
    }

    @Test
    @SuppressWarnings("null")
    void crear_usuarioYaTieneActiva_lanzaExcepcion() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(suscripcionRepository.findByUsuarioIdAndEstado(1L, EstadoSuscripcion.ACTIVA))
                .thenReturn(Optional.of(suscripcion));

        assertThatThrownBy(() -> suscripcionService.crear(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ya tiene una suscripción activa");
    }

    @Test
    @SuppressWarnings("null")
    void cancelar_exitoso() {
        when(suscripcionRepository.findById(1L)).thenReturn(Optional.of(suscripcion));

        SuscripcionResponseDTO response = suscripcionService.cancelar(1L);

        assertThat(response.getEstado()).isEqualTo(EstadoSuscripcion.CANCELADA);
        verify(suscripcionRepository).save(suscripcion);
    }

    @Test
    @SuppressWarnings("null")
    void cancelar_suscripcionNoActiva_lanzaExcepcion() {
        suscripcion.setEstado(EstadoSuscripcion.CANCELADA);
        when(suscripcionRepository.findById(1L)).thenReturn(Optional.of(suscripcion));

        assertThatThrownBy(() -> suscripcionService.cancelar(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Solo se pueden cancelar suscripciones activas");
    }

    @Test
    @SuppressWarnings("null")
    void listarPorUsuario_retornaLista() {
        when(suscripcionRepository.findByUsuarioId(1L)).thenReturn(List.of(suscripcion));

        List<SuscripcionResponseDTO> resultado = suscripcionService.listarPorUsuario(1L);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getNombreUsuario()).isEqualTo("William Admin");
    }
}