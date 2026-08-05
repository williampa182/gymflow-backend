package com.gymflow.backend.dto;

import com.gymflow.backend.model.enums.MetodoAsistencia;
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
public class AsistenciaResponseDTO {
    private Long id;
    private Long usuarioId;
    private String nombre;
    private LocalDate fecha;
    private LocalDateTime entradaEn;
    private LocalDateTime salidaEn;
    private MetodoAsistencia metodo;
}