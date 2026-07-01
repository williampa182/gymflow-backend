package com.gymflow.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SuscripcionRequestDTO {

    @NotNull(message = "El usuario es obligatorio")
    private Long usuarioId;

    @NotNull(message = "El plan es obligatorio")
    private Long planId;

    @NotNull(message = "La fecha de inicio es obligatoria")
    private LocalDate fechaInicio;
}