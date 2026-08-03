package com.gymflow.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDate;

/**
 * Body del self-service {@code POST /api/suscripciones/mi} (Fase 3).
 * A propósito NO lleva usuarioId: la identidad del inscrito sale del JWT
 * (authentication.getName()), jamás del cuerpo — un CLIENTE no puede
 * inscribirse a nombre de otro. fechaInicio es opcional: null se resuelve a
 * hoy en el service; @PastOrPresent solo se evalúa si viene valor.
 */
@Data
public class InscripcionRequestDTO {

    @NotNull(message = "El plan es obligatorio")
    private Long planId;

    @PastOrPresent(message = "La fecha de inicio no puede ser futura")
    private LocalDate fechaInicio;
}
