package com.gymflow.backend.repository.projection;

import java.time.LocalDate;

/**
 * Conteo agrupado de asistencias por fecha (Fase 5, endpoint #13): alimenta
 * el gráfico de la semana del dashboard ADMIN con UNA query. Los días sin
 * asistencias no aparecen acá — el servicio los rellena con cero.
 */
public interface AsistenciaPorFechaProjection {

    LocalDate getFecha();

    long getCantidad();
}
