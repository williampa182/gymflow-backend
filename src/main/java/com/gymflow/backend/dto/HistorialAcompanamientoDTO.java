package com.gymflow.backend.dto;

import com.gymflow.backend.model.AsignacionEntrenador;

import java.time.LocalDateTime;

/**
 * Entrada del historial de acompañamientos del cliente autenticado
 * (GET /api/entrenador/mi-historial): todas sus asignaciones, ACTIVAS y
 * canceladas. Nunca expone el email del entrenador.
 */
public record HistorialAcompanamientoDTO(
        Long id,
        String entrenadorNombre,
        boolean activa,
        LocalDateTime asignadoEn
) {
    public static HistorialAcompanamientoDTO from(AsignacionEntrenador asignacion) {
        return new HistorialAcompanamientoDTO(
                asignacion.getId(),
                asignacion.getEntrenador().getNombre(),
                asignacion.isActiva(),
                asignacion.getAsignadoEn()
        );
    }
}
