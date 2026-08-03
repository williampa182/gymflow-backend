package com.gymflow.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * Creación/edición de una rutina. Sin entrenadorId ni estado: el entrenador
 * es el usuario autenticado (RutinaService lo setea con identity.getName()),
 * y el estado activo es una acción separada del CRUD.
 */
public record RutinaRequestDTO(
        @NotBlank(message = "El nombre de la rutina es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
        String nombre,

        @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
        String descripcion,

        @NotNull(message = "Los ejercicios son obligatorios")
        @Size(min = 1, message = "Una rutina debe tener al menos un ejercicio")
        List<@Valid EjercicioRequestDTO> ejercicios
) {
}
