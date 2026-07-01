package com.gymflow.backend.dto;

import com.gymflow.backend.model.enums.TipoPlan;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PlanRequestDTO {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    private String descripcion;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor a 0")
    private BigDecimal precio;

    @NotNull(message = "La duración es obligatoria")
    @Min(value = 1, message = "La duración debe ser al menos 1 día")
    private Integer duracionDias;

    @NotNull(message = "El tipo es obligatorio")
    private TipoPlan tipo;

    private Integer limiteClases;

    private boolean incluyeClases = false;

    private boolean incluyeEntrenadorPersonal = false;
}