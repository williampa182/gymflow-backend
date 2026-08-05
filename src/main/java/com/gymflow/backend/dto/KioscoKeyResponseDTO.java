package com.gymflow.backend.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Respuesta única de la rotación de la key del kiosco (POST
 * /api/kiosco/config/rotar, Fase 5). La key sale en texto plano UNA sola vez
 * (la guarda el ADMIN al configurar el dispositivo) y nunca se loguea; la
 * anterior deja de ser válida de inmediato.
 */
@Data
@Builder
public class KioscoKeyResponseDTO {

    private String key;
}
