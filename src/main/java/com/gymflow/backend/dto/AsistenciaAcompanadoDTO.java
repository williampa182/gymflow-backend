package com.gymflow.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Semana de un cliente acompañado por el ENTRENADOR (Fase 5, endpoint #4).
 * Solo asignaciones ACTIVAS; si el cliente no marcó nada en la semana la
 * lista de asistencias viene vacía ("no vino esta semana", la UI lo
 * distingue de "no acompañado"). Sin email: molde SuscripcionResponseDTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsistenciaAcompanadoDTO {
    private Long clienteId;
    private String clienteNombre;
    private List<AsistenciaResponseDTO> asistencias;
}
