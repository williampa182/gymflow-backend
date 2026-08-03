package com.gymflow.backend.dto;

import com.gymflow.backend.model.Rutina;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Rutina como la ve su ENTRENADOR creador (GET /api/rutinas) o el CLIENTE
 * (GET /api/rutinas/mias). El campo {@code asignados} solo se puebla en la
 * vista del ENTRENADOR (clientes que ya ve por clientes-elegibles); para
 * el CLIENTE siempre llega vacío — no se filtra qué otros clientes tienen
 * la misma rutina.
 */
public record RutinaResponseDTO(
        Long id,
        String nombre,
        String descripcion,
        boolean activo,
        LocalDateTime creadoEn,
        List<EjercicioResponseDTO> ejercicios,
        List<ClienteAsignadoDTO> asignados
) {
    public static RutinaResponseDTO from(Rutina rutina) {
        return from(rutina, List.of());
    }

    public static RutinaResponseDTO from(Rutina rutina, List<ClienteAsignadoDTO> asignados) {
        return new RutinaResponseDTO(
                rutina.getId(),
                rutina.getNombre(),
                rutina.getDescripcion(),
                rutina.isActivo(),
                rutina.getCreadoEn(),
                rutina.getEjercicios().stream().map(EjercicioResponseDTO::from).toList(),
                asignados
        );
    }
}
