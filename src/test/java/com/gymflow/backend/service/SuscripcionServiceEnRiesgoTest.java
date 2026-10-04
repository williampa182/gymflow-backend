package com.gymflow.backend.service;

import com.gymflow.backend.dto.EnRiesgoDTO;
import com.gymflow.backend.model.Asistencia;
import com.gymflow.backend.model.Suscripcion;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.EstadoSuscripcion;
import com.gymflow.backend.repository.AsistenciaRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuscripcionServiceEnRiesgoTest {

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

    @BeforeEach
    void setUp() {
        suscripcionService = new SuscripcionService(
                suscripcionRepository, usuarioRepository, planRepository,
                asistenciaRepository, clock);
    }

    private static Usuario usuario(long id, String nombre) {
        return Usuario.builder().id(id).nombre(nombre)
                .email("u" + id + "@gymflow.test").password("hashed").build();
    }

    private static Suscripcion suscripcion(Usuario u, LocalDate inicio, LocalDate fin) {
        return Suscripcion.builder().usuario(u).fechaInicio(inicio).fechaFin(fin)
                .estado(EstadoSuscripcion.ACTIVA).build();
    }

    private static Asistencia asistencia(Usuario u, LocalDate fecha) {
        return Asistencia.builder().usuario(u).fecha(fecha).build();
    }

    @Test
    void porVencer_mapeaDiasRestantes() {
        LocalDate hoy = LocalDate.now(clock);
        Usuario ana = usuario(1L, "Ana");
        when(suscripcionRepository.findByEstadoAndFechaFinBetween(
                EstadoSuscripcion.ACTIVA, hoy, hoy.plusDays(7)))
                .thenReturn(List.of(suscripcion(ana, hoy.minusDays(27), hoy.plusDays(3))));
        when(suscripcionRepository.findByEstadoAndFechaFinGreaterThanEqual(
                EstadoSuscripcion.ACTIVA, hoy)).thenReturn(List.of());

        EnRiesgoDTO dto = suscripcionService.sociosEnRiesgo();

        assertThat(dto.porVencer()).hasSize(1);
        EnRiesgoDTO.PorVencerDTO pv = dto.porVencer().get(0);
        assertThat(pv.usuarioId()).isEqualTo(1L);
        assertThat(pv.nombre()).isEqualTo("Ana");
        assertThat(pv.fechaFin()).isEqualTo(hoy.plusDays(3));
        assertThat(pv.diasRestantes()).isEqualTo(3L);
        assertThat(dto.inactivos()).isEmpty();
    }

    @Test
    void inactivo_conUltimaVisitaVieja_usaMaxFecha() {
        LocalDate hoy = LocalDate.now(clock);
        Usuario beto = usuario(2L, "Beto");
        Suscripcion s = suscripcion(beto, hoy.minusDays(60), hoy.plusDays(30));
        when(suscripcionRepository.findByEstadoAndFechaFinBetween(
                EstadoSuscripcion.ACTIVA, hoy, hoy.plusDays(7))).thenReturn(List.of());
        when(suscripcionRepository.findByEstadoAndFechaFinGreaterThanEqual(
                EstadoSuscripcion.ACTIVA, hoy)).thenReturn(List.of(s));
        when(asistenciaRepository.findByUsuarioIdInAndFechaBetween(
                List.of(2L), hoy.minusDays(15), hoy))
                .thenReturn(List.of(asistencia(beto, hoy.minusDays(20))));

        EnRiesgoDTO dto = suscripcionService.sociosEnRiesgo();

        assertThat(dto.inactivos()).hasSize(1);
        EnRiesgoDTO.InactivoDTO in = dto.inactivos().get(0);
        assertThat(in.usuarioId()).isEqualTo(2L);
        assertThat(in.ultimaAsistencia()).isEqualTo(hoy.minusDays(20));
        assertThat(in.diasSinVenir()).isEqualTo(20L);
    }

    @Test
    void inactivo_nuncaVino_usaFechaInicio() {
        LocalDate hoy = LocalDate.now(clock);
        Usuario celi = usuario(3L, "Celi");
        Suscripcion s = suscripcion(celi, hoy.minusDays(30), hoy.plusDays(60));
        when(suscripcionRepository.findByEstadoAndFechaFinBetween(
                EstadoSuscripcion.ACTIVA, hoy, hoy.plusDays(7))).thenReturn(List.of());
        when(suscripcionRepository.findByEstadoAndFechaFinGreaterThanEqual(
                EstadoSuscripcion.ACTIVA, hoy)).thenReturn(List.of(s));
        when(asistenciaRepository.findByUsuarioIdInAndFechaBetween(
                List.of(3L), hoy.minusDays(15), hoy)).thenReturn(List.of());

        EnRiesgoDTO dto = suscripcionService.sociosEnRiesgo();

        assertThat(dto.inactivos()).hasSize(1);
        EnRiesgoDTO.InactivoDTO in = dto.inactivos().get(0);
        assertThat(in.ultimaAsistencia()).isNull();
        assertThat(in.diasSinVenir()).isEqualTo(30L);
    }

    @Test
    void conAsistenciaReciente_noEsInactivo() {
        LocalDate hoy = LocalDate.now(clock);
        Usuario dani = usuario(4L, "Dani");
        Suscripcion s = suscripcion(dani, hoy.minusDays(10), hoy.plusDays(20));
        when(suscripcionRepository.findByEstadoAndFechaFinBetween(
                EstadoSuscripcion.ACTIVA, hoy, hoy.plusDays(7))).thenReturn(List.of());
        when(suscripcionRepository.findByEstadoAndFechaFinGreaterThanEqual(
                EstadoSuscripcion.ACTIVA, hoy)).thenReturn(List.of(s));
        when(asistenciaRepository.findByUsuarioIdInAndFechaBetween(
                List.of(4L), hoy.minusDays(15), hoy))
                .thenReturn(List.of(asistencia(dani, hoy.minusDays(2))));

        EnRiesgoDTO dto = suscripcionService.sociosEnRiesgo();

        assertThat(dto.inactivos()).isEmpty();
    }

    @Test
    void inactivos_ordenadosPorDiasSinVenirDesc() {
        LocalDate hoy = LocalDate.now(clock);
        Usuario beto = usuario(2L, "Beto");
        Usuario celi = usuario(3L, "Celi");
        when(suscripcionRepository.findByEstadoAndFechaFinBetween(
                EstadoSuscripcion.ACTIVA, hoy, hoy.plusDays(7))).thenReturn(List.of());
        when(suscripcionRepository.findByEstadoAndFechaFinGreaterThanEqual(
                EstadoSuscripcion.ACTIVA, hoy)).thenReturn(List.of(
                        suscripcion(beto, hoy.minusDays(60), hoy.plusDays(30)),
                        suscripcion(celi, hoy.minusDays(40), hoy.plusDays(50))));
        when(asistenciaRepository.findByUsuarioIdInAndFechaBetween(
                List.of(2L, 3L), hoy.minusDays(15), hoy))
                .thenReturn(List.of(asistencia(beto, hoy.minusDays(20))));

        EnRiesgoDTO dto = suscripcionService.sociosEnRiesgo();

        assertThat(dto.inactivos()).hasSize(2);
        assertThat(dto.inactivos().get(0).usuarioId()).isEqualTo(3L);
        assertThat(dto.inactivos().get(0).diasSinVenir()).isEqualTo(40L);
        assertThat(dto.inactivos().get(1).usuarioId()).isEqualTo(2L);
        assertThat(dto.inactivos().get(1).diasSinVenir()).isEqualTo(20L);
    }

    @Test
    void vacio_devuelveListasVacias() {
        LocalDate hoy = LocalDate.now(clock);
        when(suscripcionRepository.findByEstadoAndFechaFinBetween(
                EstadoSuscripcion.ACTIVA, hoy, hoy.plusDays(7))).thenReturn(List.of());
        when(suscripcionRepository.findByEstadoAndFechaFinGreaterThanEqual(
                EstadoSuscripcion.ACTIVA, hoy)).thenReturn(List.of());

        EnRiesgoDTO dto = suscripcionService.sociosEnRiesgo();

        assertThat(dto.porVencer()).isEmpty();
        assertThat(dto.inactivos()).isEmpty();
        verifyNoInteractions(asistenciaRepository);
    }
}
