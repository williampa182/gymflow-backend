package com.gymflow.backend.service;

import com.gymflow.backend.dto.dashboard.DashboardAdminStatsResponse;
import com.gymflow.backend.model.enums.EstadoSuscripcion;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.model.enums.TipoPlan;
import com.gymflow.backend.repository.SuscripcionRepository;
import com.gymflow.backend.repository.UsuarioRepository;
import com.gymflow.backend.repository.projection.IngresoPorTipoPlanProjection;
import com.gymflow.backend.repository.projection.SuscripcionPorEstadoProjection;
import com.gymflow.backend.repository.projection.UsuarioPorRolProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardAdminServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private SuscripcionRepository suscripcionRepository;

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
