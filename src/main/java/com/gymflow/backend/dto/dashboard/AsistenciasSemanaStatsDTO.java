package com.gymflow.backend.dto.dashboard;

import java.time.LocalDate;
import java.util.List;

/**
 * Stats de asistencias de la semana para el dashboard ADMIN (Fase 5,
 * endpoint #13). Una sola query agrupada (contarPorFecha) alimenta ambos
 * valores: asistenciasHoy sale del mismo mapa, no de otra consulta.
 */
public record AsistenciasSemanaStatsDTO(
        long asistenciasHoy,
        List<AsistenciaDiaStat> asistenciasSemana
) {

    public record AsistenciaDiaStat(
            LocalDate fecha,
            long cantidad
    ) {}
}
