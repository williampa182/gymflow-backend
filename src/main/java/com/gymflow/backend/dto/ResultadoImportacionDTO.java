package com.gymflow.backend.dto;

import java.util.List;

public record ResultadoImportacionDTO(
        int totalFilas,
        int creados,
        List<FilaOmitidaDTO> omitidos,
        List<FilaOmitidaDTO> errores) {

    public record FilaOmitidaDTO(int fila, String motivo) {
    }
}
