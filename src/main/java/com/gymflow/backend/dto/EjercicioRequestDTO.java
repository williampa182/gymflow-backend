package com.gymflow.backend.dto;

import jakarta.validation.constraints.*;

/**
 * Ejercicio dentro de una rutina. El id solo se usa al ACTUALIZAR (PUT):
 * sin id = ejercicio nuevo, con id = actualización del existente.
 * El cliente nunca puede setear su propia posición (orden) ni su rutina:
 * ambas las controla el servidor (el orden se deriva del índice en la lista,
 * la rutina se re-enlaza en RutinaService).
 */
public record EjercicioRequestDTO(
        Long id,

        @NotBlank(message = "El nombre del ejercicio es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
        String nombre,

        @NotNull(message = "Las series son obligatorias")
        @Min(value = 1, message = "Las series deben ser al menos 1")
        @Max(value = 100, message = "Las series no pueden superar 100")
        Integer series,

        @NotNull(message = "Las repeticiones son obligatorias")
        @Min(value = 1, message = "Las repeticiones deben ser al menos 1")
        @Max(value = 1000, message = "Las repeticiones no pueden superar 1000")
        Integer repeticiones
) {
}
