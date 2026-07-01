package com.gymflow.backend.dto;

import com.gymflow.backend.model.enums.EstadoSuscripcion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuscripcionResponseDTO {
    private Long id;
    private Long usuarioId;
    private String nombreUsuario;
    private Long planId;
    private String nombrePlan;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private EstadoSuscripcion estado;
    private LocalDateTime creadoEn;
}