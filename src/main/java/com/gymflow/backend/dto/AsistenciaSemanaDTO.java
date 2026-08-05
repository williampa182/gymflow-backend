package com.gymflow.backend.dto;

import com.gymflow.backend.model.Asistencia;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsistenciaSemanaDTO {
    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
    private int total;
    private List<AsistenciaResponseDTO> asistencias;

    public static AsistenciaSemanaDTO from(LocalDate desde, LocalDate hasta, List<Asistencia> asistencias) {
        List<AsistenciaResponseDTO> dtos = asistencias.stream()
                .map(a -> AsistenciaResponseDTO.builder()
                        .id(a.getId())
                        .usuarioId(a.getUsuario().getId())
                        .nombre(a.getUsuario().getNombre())
                        .fecha(a.getFecha())
                        .entradaEn(a.getEntradaEn())
                        .salidaEn(a.getSalidaEn())
                        .metodo(a.getMetodo())
                        .build())
                .toList();
        return AsistenciaSemanaDTO.builder()
                .fechaDesde(desde)
                .fechaHasta(hasta)
                .total(dtos.size())
                .asistencias(dtos)
                .build();
    }
}