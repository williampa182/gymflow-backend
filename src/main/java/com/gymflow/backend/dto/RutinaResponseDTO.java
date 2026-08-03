package com.gymflow.backend.dto;

import com.gymflow.backend.model.Rutina;

import java.time.LocalDateTime;
import java.util.List;

public record RutinaResponseDTO(
        Long id,
        String nombre,
        String descripcion,
        boolean activo,
        LocalDateTime creadoEn,
        List<EjercicioResponseDTO> ejercicios
) {
    public static RutinaResponseDTO from(Rutina rutina) {
        return new RutinaResponseDTO(
                rutina.getId(),
                rutina.getNombre(),
                rutina.getDescripcion(),
                rutina.isActivo(),
                rutina.getCreadoEn(),
                rutina.getEjercicios().stream().map(EjercicioResponseDTO::from).toList()
        );
    }
}
