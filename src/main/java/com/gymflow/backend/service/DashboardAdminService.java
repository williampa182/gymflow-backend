package com.gymflow.backend.service;

import com.gymflow.backend.dto.dashboard.AsistenciasSemanaStatsDTO;
import com.gymflow.backend.dto.dashboard.AsistenciasSemanaStatsDTO.AsistenciaDiaStat;
import com.gymflow.backend.dto.dashboard.DashboardAdminStatsResponse;
import com.gymflow.backend.dto.dashboard.DashboardAdminStatsResponse.IngresoPorTipoPlanStat;
import com.gymflow.backend.dto.dashboard.DashboardAdminStatsResponse.SuscripcionPorEstadoStat;
import com.gymflow.backend.dto.dashboard.DashboardAdminStatsResponse.UsuarioPorRolStat;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardAdminService {

    private final UsuarioRepository usuarioRepository;
    private final SuscripcionRepository suscripcionRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final Clock clock;

    public DashboardAdminStatsResponse obtenerEstadisticas() {
        return new DashboardAdminStatsResponse(
                usuariosPorRol(),
                ingresosPorTipoPlan(),
                suscripcionesPorEstado()
        );
    }

    /**
     * Asistencias de la semana (Fase 5, endpoint #13). UNA query agrupada
     * (contarPorFecha) alimenta los siete días rellenados con cero y el
     * "asistenciasHoy" del mismo mapa — no hay segunda consulta. Semana ISO
     * lunes→domingo en hora Bogotá (Clock, regla 7).
     */
    @Transactional(readOnly = true)
    public AsistenciasSemanaStatsDTO obtenerAsistenciasSemana() {
        LocalDate hoy = LocalDate.now(clock);
        LocalDate desde = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate hasta = desde.plusDays(6);

        Map<LocalDate, Long> porFecha = new HashMap<>();
        asistenciaRepository.contarPorFecha(desde, hasta)
                .forEach(row -> porFecha.put(row.getFecha(), row.getCantidad()));

        List<AsistenciaDiaStat> semana = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate dia = desde.plusDays(i);
            semana.add(new AsistenciaDiaStat(dia, porFecha.getOrDefault(dia, 0L)));
        }
        return new AsistenciasSemanaStatsDTO(porFecha.getOrDefault(hoy, 0L), semana);
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
