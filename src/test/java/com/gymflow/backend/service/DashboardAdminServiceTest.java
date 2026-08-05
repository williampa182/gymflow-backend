package com.gymflow.backend.service;

import com.gymflow.backend.dto.dashboard.AsistenciasSemanaStatsDTO;
import com.gymflow.backend.dto.dashboard.DashboardAdminStatsResponse;
import com.gymflow.backend.model.enums.EstadoSuscripcion;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.model.enums.TipoPlan;
import com.gymflow.backend.repository.AsistenciaRepository;
import com.gymflow.backend.repository.SuscripcionRepository;
import com.gymflow.backend.repository.UsuarioRepository;
import com.gymflow.backend.repository.projection.AsistenciaPorFechaProjection;
import com.gymflow.backend.repository.projection.IngresoPorTipoPlanProjection;
import com.gymflow.backend.repository.projection.SuscripcionPorEstadoProjection;
import com.gymflow.backend.repository.projection.UsuarioPorRolProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardAdminServiceTest {

    // 2026-08-03 es lunes. 15:00 UTC = 10:00 Bogotá.
    private static final Instant INSTANTE = Instant.parse("2026-08-03T15:00:00Z");
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalDate HOY = LocalDate.of(2026, 8, 3);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 8, 9);

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private SuscripcionRepository suscripcionRepository;

    @Mock
    private AsistenciaRepository asistenciaRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private DashboardAdminService dashboardAdminService;

    @Test
    void obtenerEstadisticas_completaCategoriasFaltantesConCero() {
        when(usuarioRepository.contarUsuariosActivosPorRol()).thenReturn(List.of(
                usuarioPorRol(Rol.ADMIN, 2),
                usuarioPorRol(Rol.CLIENTE, 10)
        ));
        when(suscripcionRepository.ingresosEstimadosPorTipoPlan(EstadoSuscripcion.ACTIVA))
                .thenReturn(List.of(ingresoPorTipoPlan(TipoPlan.MENSUAL, "50000.00", 3)));
        when(suscripcionRepository.contarSuscripcionesPorEstado()).thenReturn(List.of(
                suscripcionPorEstado(EstadoSuscripcion.ACTIVA, 3),
                suscripcionPorEstado(EstadoSuscripcion.CANCELADA, 1)
        ));

        DashboardAdminStatsResponse resultado = dashboardAdminService.obtenerEstadisticas();

        assertThat(resultado.usuariosPorRol())
                .extracting("rol")
                .containsExactly(Rol.ADMIN, Rol.ENTRENADOR, Rol.CLIENTE);
        assertThat(resultado.usuariosPorRol().get(1).cantidad()).isZero();

        assertThat(resultado.ingresosPorTipoPlan())
                .extracting("tipoPlan")
                .containsExactly(TipoPlan.MENSUAL, TipoPlan.TRIMESTRAL, TipoPlan.SEMESTRAL, TipoPlan.ANUAL);
        assertThat(resultado.ingresosPorTipoPlan().get(0).ingresoEstimado())
                .isEqualByComparingTo("50000.00");
        assertThat(resultado.ingresosPorTipoPlan().get(1).ingresoEstimado())
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(resultado.suscripcionesPorEstado())
                .extracting("estado")
                .containsExactly(EstadoSuscripcion.ACTIVA, EstadoSuscripcion.VENCIDA, EstadoSuscripcion.CANCELADA);
        assertThat(resultado.suscripcionesPorEstado().get(1).cantidad()).isZero();
    }

    @Test
    void obtenerAsistenciasSemana_unaQueryRellenaLosSieteDiasConCero() {
        when(clock.instant()).thenReturn(INSTANTE);
        when(clock.getZone()).thenReturn(BOGOTA);
        when(asistenciaRepository.contarPorFecha(HOY, DOMINGO)).thenReturn(List.of(
                asistenciasPorFecha(LocalDate.of(2026, 8, 3), 4),
                asistenciasPorFecha(LocalDate.of(2026, 8, 5), 2)
        ));

        AsistenciasSemanaStatsDTO resultado = dashboardAdminService.obtenerAsistenciasSemana();

        assertThat(resultado.asistenciasHoy()).isEqualTo(4);
        assertThat(resultado.asistenciasSemana()).hasSize(7);
        assertThat(resultado.asistenciasSemana()).extracting("fecha")
                .containsExactly(
                        LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 5),
                        LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 8),
                        LocalDate.of(2026, 8, 9));
        // el 5 (miércoles) mantiene su conteo; el resto, cero
        assertThat(resultado.asistenciasSemana().get(2).cantidad()).isEqualTo(2);
        assertThat(resultado.asistenciasSemana().get(1).cantidad()).isZero();
        assertThat(resultado.asistenciasSemana().get(6).cantidad()).isZero();
    }

    @Test
    void obtenerAsistenciasSemana_sinAsistencias_asistenciasHoyCero() {
        when(clock.instant()).thenReturn(INSTANTE);
        when(clock.getZone()).thenReturn(BOGOTA);
        when(asistenciaRepository.contarPorFecha(HOY, DOMINGO)).thenReturn(List.of());

        AsistenciasSemanaStatsDTO resultado = dashboardAdminService.obtenerAsistenciasSemana();

        assertThat(resultado.asistenciasHoy()).isZero();
        assertThat(resultado.asistenciasSemana()).allMatch(d -> d.cantidad() == 0);
    }

    private AsistenciaPorFechaProjection asistenciasPorFecha(LocalDate fecha, long cantidad) {
        return new AsistenciaPorFechaProjection() {
            @Override
            public LocalDate getFecha() {
                return fecha;
            }

            @Override
            public long getCantidad() {
                return cantidad;
            }
        };
    }

    private UsuarioPorRolProjection usuarioPorRol(Rol rol, long cantidad) {
        return new UsuarioPorRolProjection() {
            @Override
            public Rol getRol() {
                return rol;
            }

            @Override
            public long getCantidad() {
                return cantidad;
            }
        };
    }

    private IngresoPorTipoPlanProjection ingresoPorTipoPlan(
            TipoPlan tipoPlan,
            String ingresoEstimado,
            long cantidadSuscripciones) {
        return new IngresoPorTipoPlanProjection() {
            @Override
            public TipoPlan getTipoPlan() {
                return tipoPlan;
            }

            @Override
            public BigDecimal getIngresoEstimado() {
                return new BigDecimal(ingresoEstimado);
            }

            @Override
            public long getCantidadSuscripciones() {
                return cantidadSuscripciones;
            }
        };
    }

    private SuscripcionPorEstadoProjection suscripcionPorEstado(
            EstadoSuscripcion estado,
            long cantidad) {
        return new SuscripcionPorEstadoProjection() {
            @Override
            public EstadoSuscripcion getEstado() {
                return estado;
            }

            @Override
            public long getCantidad() {
                return cantidad;
            }
        };
    }
}
