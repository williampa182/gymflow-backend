package com.gymflow.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Body del check-in manual del ADMIN (Fase 5, POST /api/asistencias/admin/
 * marcar). Solo usuarioId: el método (ADMIN) lo decide el servidor y el
 * email del ADMIN autenticado no entra al dominio (regla anti
 * mass-assignment, THREAT_MODEL §9).
 */
@Data
public class AdminMarcarAsistenciaRequestDTO {

    @NotNull(message = "El usuarioId es obligatorio")
    private Long usuarioId;
}
