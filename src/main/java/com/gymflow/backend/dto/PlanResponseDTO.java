package com.gymflow.backend.dto;

import com.gymflow.backend.model.enums.TipoPlan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanResponseDTO {
    private Long id;
    private String nombre;
    private String descripcion;
    private BigDecimal precio;
    private Integer duracionDias;
    private TipoPlan tipo;
    private Integer limiteClases;
    private boolean incluyeClases;
    private boolean incluyeEntrenadorPersonal;
    private boolean activo;
    private LocalDateTime creadoEn;
}