package com.gymflow.backend.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Estado de configuración del kiosco (GET /api/kiosco/config, Fase 5). Nunca
 * incluye la key (ni su hash): es un booleano para que la UI ADMIN sepa si
 * puede ofrecer "Configurar kiosco".
 */
@Data
@Builder
public class KioscoConfigResponseDTO {

    private boolean configurada;
}
