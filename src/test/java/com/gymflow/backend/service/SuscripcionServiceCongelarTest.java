package com.gymflow.backend.service;

import com.gymflow.backend.dto.SuscripcionResponseDTO;
import com.gymflow.backend.model.Plan;
import com.gymflow.backend.model.Suscripcion;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.EstadoSuscripcion;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.repository.PlanRepository;
import com.gymflow.backend.repository.AsistenciaRepository;
import com.gymflow.backend.repository.SuscripcionRepository;
import com.gymflow.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuscripcionServiceCongelarTest {

    @Mock
    private SuscripcionRepository suscripcionRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private AsistenciaRepository asistenciaRepository;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-10-03T12:00:00Z"), ZoneId.of("America/Bogota"));

    private SuscripcionService suscripcionService;

    private Usuario usuario;
    private Plan plan;
    private Suscripcion suscripcion;

    @BeforeEach
    void setUp() {
        suscripcionService = new SuscripcionService(
                suscripcionRepository, usuarioRepository, planRepository,
                asistenciaRepository, clock);
        usuario = Usuario.builder().id(1L).nombre("Socio").rol(Rol.CLIENTE).build();
        plan = Plan.builder().id(1L).nombre("Mensual").duracionDias(30).build();
        suscripcion = Suscripcion.builder()
                .id(9L)
                .usuario(usuario)
                .plan(plan)
                .fechaInicio(LocalDate.now(clock).minusDays(5))
                .fechaFin(LocalDate.now(clock).plusDays(25))
                .estado(EstadoSuscripcion.ACTIVA)
                .build();
    }

    @Test
    void congelar_activaVigente_pasaACongeladaConFecha() {
        when(suscripcionRepository.findById(9L)).thenReturn(Optional.of(suscripcion));

        SuscripcionResponseDTO respuesta = suscripcionService.congelar(9L);

        assertThat(respuesta.getEstado()).isEqualTo(EstadoSuscripcion.CONGELADA);
        assertThat(respuesta.getCongeladaDesde()).isEqualTo(LocalDate.now(clock));
        verify(suscripcionRepository).save(suscripcion);
    }

    @Test
    void congelar_vencidaDeFacto_lanzaExcepcion() {
        suscripcion.setFechaFin(LocalDate.now(clock).minusDays(1));
        when(suscripcionRepository.findById(9L)).thenReturn(Optional.of(suscripcion));

        assertThatThrownBy(() -> suscripcionService.congelar(9L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Solo se pueden congelar");
        verify(suscripcionRepository, never()).save(any());
    }

    @Test
    void congelar_yaCongelada_lanzaExcepcion() {
        suscripcion.setEstado(EstadoSuscripcion.CONGELADA);
        when(suscripcionRepository.findById(9L)).thenReturn(Optional.of(suscripcion));

        assertThatThrownBy(() -> suscripcionService.congelar(9L))
                .isInstanceOf(RuntimeException.class);
        verify(suscripcionRepository, never()).save(any());
    }

    @Test
    void descongelar_extiendeFechaFinPorDiasCongelados() {
        suscripcion.setEstado(EstadoSuscripcion.CONGELADA);
        suscripcion.setCongeladaDesde(LocalDate.now(clock).minusDays(10));
        LocalDate finOriginal = suscripcion.getFechaFin();
        when(suscripcionRepository.findById(9L)).thenReturn(Optional.of(suscripcion));

        SuscripcionResponseDTO respuesta = suscripcionService.descongelar(9L);

        assertThat(respuesta.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(suscripcion.getFechaFin()).isEqualTo(finOriginal.plusDays(10));
        assertThat(suscripcion.getCongeladaDesde()).isNull();
    }

    @Test
    void descongelar_noCongelada_lanzaExcepcion() {
        when(suscripcionRepository.findById(9L)).thenReturn(Optional.of(suscripcion));

        assertThatThrownBy(() -> suscripcionService.descongelar(9L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("descongelar");
        verify(suscripcionRepository, never()).save(any());
    }

    @Test
    void cancelar_congelada_permite() {
        suscripcion.setEstado(EstadoSuscripcion.CONGELADA);
        when(suscripcionRepository.findById(9L)).thenReturn(Optional.of(suscripcion));

        SuscripcionResponseDTO respuesta = suscripcionService.cancelar(9L);

        assertThat(respuesta.getEstado()).isEqualTo(EstadoSuscripcion.CANCELADA);
    }
}
