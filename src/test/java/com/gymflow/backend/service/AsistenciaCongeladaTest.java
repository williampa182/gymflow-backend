package com.gymflow.backend.service;

import com.gymflow.backend.model.Suscripcion;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.EstadoSuscripcion;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.repository.AsignacionEntrenadorRepository;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsistenciaCongeladaTest {

    @Mock
    private AsistenciaRepository asistenciaRepository;

    @Mock
    private SuscripcionRepository suscripcionRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private KioscoConfigService kioscoConfigService;

    @Mock
    private AsignacionEntrenadorRepository asignacionEntrenadorRepository;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-10-03T12:00:00Z"), ZoneId.of("America/Bogota"));

    private AsistenciaService asistenciaService;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        asistenciaService = new AsistenciaService(
                asistenciaRepository, suscripcionRepository, usuarioRepository,
                kioscoConfigService, asignacionEntrenadorRepository, clock);
        usuario = Usuario.builder()
                .id(1L).nombre("Socio").email("socio@test.com")
                .rol(Rol.CLIENTE).activo(true).codigoCarnet("ABC123").build();
        Suscripcion congelada = Suscripcion.builder()
                .id(9L).usuario(usuario)
                .fechaInicio(LocalDate.now(clock).minusDays(5))
                .fechaFin(LocalDate.now(clock).plusDays(25))
                .estado(EstadoSuscripcion.CONGELADA)
                .congeladaDesde(LocalDate.now(clock).minusDays(2))
                .build();
        when(suscripcionRepository.findByUsuarioIdAndEstado(1L, EstadoSuscripcion.CONGELADA))
                .thenReturn(Optional.of(congelada));
    }

    @Test
    void marcarMi_congelada_deniegaConMensajePropio() {
        when(usuarioRepository.findByEmail("socio@test.com")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> asistenciaService.marcarMi("socio@test.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("congelada");
    }

    @Test
    void marcarKiosk_congelada_deniegaConMensajePropio() {
        when(kioscoConfigService.validar("key")).thenReturn(true);
        when(usuarioRepository.findByCodigoCarnet("ABC123")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> asistenciaService.marcarKiosk("ABC123", "key"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("congelada");
    }

    @Test
    void adminMarcar_congelada_deniegaConMensajePropio() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> asistenciaService.adminMarcar(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("congelada");
    }
}
