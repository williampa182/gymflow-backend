package com.gymflow.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Body del check-in del kiosco (Fase 5, POST /api/asistencias/kiosk). Solo el
 * código de carnet escaneado; la identidad y la credencial del dispositivo
 * vienen del header X-Kiosk-Key. Nunca lleva usuarioId ni método (regla anti
 * mass-assignment, THREAT_MODEL §9).
 */
@Data
public class KioskCheckInRequestDTO {

    @NotBlank(message = "El código de carnet es obligatorio")
    private String codigo;
}
