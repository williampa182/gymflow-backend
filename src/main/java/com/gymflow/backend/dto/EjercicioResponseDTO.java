package com.gymflow.backend.dto;

import com.gymflow.backend.model.Ejercicio;
import com.gymflow.backend.model.Rutina;

import java.time.LocalDateTime;
import java.util.List;

public record EjercicioResponseDTO(
        Long id,
        String nombre,
        Integer series,
        Integer repeticiones,
        Integer orden
) {
    public static EjercicioResponseDTO from(Ejercicio ejercicio) {
        return new EjercicioResponseDTO(
                ejercicio.getId(),
                ejercicio.getNombre(),
                ejercicio.getSeries(),
                ejercicio.getRepeticiones(),
                ejercicio.getOrden()
        );
    }
}
