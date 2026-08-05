package com.gymflow.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarnetResponseDTO {

    private String codigoCarnet;

    // Solo se puebla en la vista ADMIN (reimpresión: GET /api/usuarios/{id}/carnet).
    // En la vista CLIENTE (GET /api/asistencias/mi/carnet) queda oculto por
    // NON_NULL: el contrato de ese endpoint es solo { codigoCarnet }.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String nombre;
}
