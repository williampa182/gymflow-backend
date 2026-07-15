package com.gymflow.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SuscripcionRequestDTO {

    @NotNull(message = "El usuario es obligatorio")
    private Long usuarioId;

    @NotNull(message = "El plan es obligatorio")
    private Long planId;

    // Fix security-deep-dive §9 (GLM-5.2): sin esto se podían crear
    // suscripciones con fechaInicio en el futuro lejano o el pasado remoto,
    // rompiendo cálculos de fechaFin y métricas del dashboard. Si en el
    // futuro se quiere soportar suscripciones programadas, reemplazar por
    // un validador custom con rango acotado en vez de quitar esto.
    @NotNull(message = "La fecha de inicio es obligatoria")
    @PastOrPresent(message = "La fecha de inicio no puede ser futura")
    private LocalDate fechaInicio;
}