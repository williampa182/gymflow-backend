package com.gymflow.backend.dto;

import com.gymflow.backend.model.AsignacionEntrenador;

import java.time.LocalDateTime;

/**
 * Acompañante actual del cliente autenticado (endpoint /api/entrenador/mio).
 */
public record MiEntrenadorDTO(
        Long id,
        String nombre,
        LocalDateTime asignadoEn
) {
    public static MiEntrenadorDTO from(AsignacionEntrenador asignacion) {
        return new MiEntrenadorDTO(
                asignacion.getEntrenador().getId(),
                asignacion.getEntrenador().getNombre(),
                asignacion.getAsignadoEn()
        );
    }
}
