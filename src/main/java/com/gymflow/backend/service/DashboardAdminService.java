package com.gymflow.backend.service;

import com.gymflow.backend.dto.dashboard.DashboardAdminStatsResponse;
import com.gymflow.backend.dto.dashboard.DashboardAdminStatsResponse.IngresoPorTipoPlanStat;
import com.gymflow.backend.dto.dashboard.DashboardAdminStatsResponse.SuscripcionPorEstadoStat;
import com.gymflow.backend.dto.dashboard.DashboardAdminStatsResponse.UsuarioPorRolStat;
import com.gymflow.backend.model.enums.EstadoSuscripcion;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.model.enums.TipoPlan;
import com.gymflow.backend.repository.SuscripcionRepository;
import com.gymflow.backend.repository.UsuarioRepository;
import com.gymflow.backend.repository.projection.IngresoPorTipoPlanProjection;
import com.gymflow.backend.repository.projection.SuscripcionPorEstadoProjection;
import com.gymflow.backend.repository.projection.UsuarioPorRolProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardAdminService {

    private final UsuarioRepository usuarioRepository;
    private final SuscripcionRepository suscripcionRepository;

    public DashboardAdminStatsResponse obtenerEstadisticas() {
        return new DashboardAdminStatsResponse(
                usuariosPorRol(),
                ingresosPorTipoPlan(),
                suscripcionesPorEstado()
        );
    }

    private List<UsuarioPorRolStat> usuariosPorRol() {
        Map<Rol, Long> conteos = new EnumMap<>(Rol.class);
        usuarioRepository.contarUsuariosActivosPorRol()
                .forEach(row -> conteos.put(row.getRol(), row.getCantidad()));

        return List.of(Rol.ADMIN, Rol.ENTRENADOR, Rol.CLIENTE)
                .stream()
                .map(rol -> new UsuarioPorRolStat(rol, conteos.getOrDefault(rol, 0L)))
                .toList();
    }

    private List<IngresoPorTipoPlanStat> ingresosPorTipoPlan() {
        Map<TipoPlan, IngresoPorTipoPlanProjection> ingresos = new EnumMap<>(TipoPlan.class);
        suscripcionRepository.ingresosEstimadosPorTipoPlan(EstadoSuscripcion.ACTIVA)
                .forEach(row -> ingresos.put(row.getTipoPlan(), row));

        return List.of(TipoPlan.MENSUAL, TipoPlan.TRIMESTRAL, TipoPlan.SEMESTRAL, TipoPlan.ANUAL)
                .stream()
                .map(tipoPlan -> {
                    IngresoPorTipoPlanProjection row = ingresos.get(tipoPlan);
                    return new IngresoPorTipoPlanStat(
                            tipoPlan,
                            row != null ? row.getIngresoEstimado() : BigDecimal.ZERO,
                            row != null ? row.getCantidadSuscripciones() : 0L
                    );
                })
                .toList();
    }

    private List<SuscripcionPorEstadoStat> suscripcionesPorEstado() {
        Map<EstadoSuscripcion, Long> conteos = new EnumMap<>(EstadoSuscripcion.class);
        suscripcionRepository.contarSuscripcionesPorEstado()
                .forEach(row -> conteos.put(row.getEstado(), row.getCantidad()));

        return List.of(EstadoSuscripcion.ACTIVA, EstadoSuscripcion.VENCIDA, EstadoSuscripcion.CANCELADA)
                .stream()
                .map(estado -> new SuscripcionPorEstadoStat(estado, conteos.getOrDefault(estado, 0L)))
                .toList();
    }
}
