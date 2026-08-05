package com.gymflow.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuración del kiosco de recepción (Fase 5). Fila única deliberada
 * (id fijo 1): el kiosco es de instalación singular por tenant — una puerta,
 * una clave de dispositivo. Nunca se crea otra fila; solo se actualiza
 * {@code apiKeyHash} (BCrypt) en la siembra inicial o en la rotación.
 *
 * La clave en texto plano solo existe en: (a) KIOSK_API_KEY del entorno en el
 * primer boot (siembra) y (b) la respuesta única de POST /api/kiosco/config/rotar.
 * Nunca en logs ni en DTOs de lectura.
 */
@Entity
@Table(name = "kiosco_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KioscoConfig {

    public static final Long FILA_UNICA_ID = 1L;

    @Id
    private Long id = FILA_UNICA_ID;

    @Column(name = "api_key_hash", length = 60)
    private String apiKeyHash;
}
