package com.gymflow.backend.controller;

import com.gymflow.backend.dto.KioscoConfigResponseDTO;
import com.gymflow.backend.dto.KioscoKeyResponseDTO;
import com.gymflow.backend.service.KioscoConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuración de la credencial de dispositivo del kiosco (Fase 5,
 * endpoints #11 y #12). Solo ADMIN. Nunca expone la key en lectura ni en
 * logs: configurada() es un booleano; la clave nueva sale en texto plano una
 * única vez en la respuesta de rotar().
 */
@RestController
@RequestMapping("/api/kiosco/config")
@RequiredArgsConstructor
public class KioscoConfigController {

    private final KioscoConfigService kioscoConfigService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KioscoConfigResponseDTO> configuracion() {
        // False también cuando NO hay fila en BD (fail-closed): la UI ADMIN
        // ofrece "Configurar kiosco" recién acá.
        return ResponseEntity.ok(KioscoConfigResponseDTO.builder()
                .configurada(kioscoConfigService.configurada())
                .build());
    }

    @PostMapping("/rotar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KioscoKeyResponseDTO> rotar() {
        // Clave nueva en texto plano, única vez; la anterior pierde vigencia
        // de inmediato (BCrypt reemplazado en la fila única).
        return ResponseEntity.ok(KioscoKeyResponseDTO.builder()
                .key(kioscoConfigService.rotar())
                .build());
    }
}