package com.gymflow.backend.dto;

import java.time.LocalDate;
import java.util.List;

public record EnRiesgoDTO(List<PorVencerDTO> porVencer, List<InactivoDTO> inactivos) {
    public record PorVencerDTO(long usuarioId, String nombre, LocalDate fechaFin, long diasRestantes) {
    }

    public record InactivoDTO(long usuarioId, String nombre, LocalDate ultimaAsistencia, long diasSinVenir) {
    }
}
