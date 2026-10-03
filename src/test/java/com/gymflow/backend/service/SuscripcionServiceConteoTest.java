package com.gymflow.backend.service;

import com.gymflow.backend.dto.ConteoSuscripcionesDTO;
import com.gymflow.backend.model.enums.EstadoSuscripcion;
import com.gymflow.backend.repository.PlanRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuscripcionServiceConteoTest {

    @Mock
    private SuscripcionRepository suscripcionRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PlanRepository planRepository;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-10-03T12:00:00Z"), ZoneId.of("America/Bogota"));

    private SuscripcionService suscripcionService;

    @BeforeEach
    void setUp() {
        suscripcionService = new SuscripcionService(
                suscripcionRepository, usuarioRepository, planRepository, clock);
    }

    @Test
    void contarPorEstado_usaVigenciaDerivada() {
        LocalDate hoy = LocalDate.now(clock);
        when(suscripcionRepository.countByEstadoAndFechaFinGreaterThanEqual(
                EstadoSuscripcion.ACTIVA, hoy)).thenReturn(20L);
        when(suscripcionRepository.countMorosas(
                EstadoSuscripcion.ACTIVA, EstadoSuscripcion.VENCIDA, hoy)).thenReturn(4L);
        when(suscripcionRepository.countByEstado(EstadoSuscripcion.CANCELADA)).thenReturn(7L);

        ConteoSuscripcionesDTO conteo = suscripcionService.contarPorEstado();

        assertThat(conteo).isEqualTo(new ConteoSuscripcionesDTO(20L, 4L, 7L));
    }
}
