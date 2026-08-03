package com.gymflow.backend.controller;

import com.gymflow.backend.dto.RutinaRequestDTO;
import com.gymflow.backend.dto.RutinaResponseDTO;
import com.gymflow.backend.service.RutinaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de rutinas (Fase 4):
 *   - ENTRENADOR: /api/rutinas (CRUD + asignar/quitar a clientes acompañados)
 *   - CLIENTE:    /api/rutinas/mias (solo las asignadas y activas)
 * Toda la autorización de datos es por identidad JWT (authentication.getName())
 * + ownership verificado en RutinaService; @PreAuthorize es la barrera de rol.
 */
@RestController
@RequestMapping("/api/rutinas")
@RequiredArgsConstructor
public class RutinaController {

    private final RutinaService rutinaService;

    @GetMapping
    @PreAuthorize("hasRole('ENTRENADOR')")
    public List<RutinaResponseDTO> listarMias(Authentication authentication) {
        return rutinaService.listarMias(authentication.getName());
    }

    @GetMapping("/mias")
    @PreAuthorize("hasRole('CLIENTE')")
    public List<RutinaResponseDTO> listarAsignadas(Authentication authentication) {
        return rutinaService.listarAsignadas(authentication.getName());
    }

    @PostMapping
    @PreAuthorize("hasRole('ENTRENADOR')")
    public ResponseEntity<RutinaResponseDTO> crear(
            Authentication authentication,
            @Valid @RequestBody RutinaRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rutinaService.crear(authentication.getName(), request));
    }

    @PutMapping("/{rutinaId}")
    @PreAuthorize("hasRole('ENTRENADOR')")
    public RutinaResponseDTO actualizar(
            Authentication authentication,
            @PathVariable Long rutinaId,
            @Valid @RequestBody RutinaRequestDTO request) {
        return rutinaService.actualizar(authentication.getName(), rutinaId, request);
    }

    @DeleteMapping("/{rutinaId}")
    @PreAuthorize("hasRole('ENTRENADOR')")
    public ResponseEntity<Void> desactivar(
            Authentication authentication,
            @PathVariable Long rutinaId) {
        rutinaService.desactivar(authentication.getName(), rutinaId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{rutinaId}/asignar/{clienteId}")
    @PreAuthorize("hasRole('ENTRENADOR')")
    public ResponseEntity<Void> asignar(
            Authentication authentication,
            @PathVariable Long rutinaId,
            @PathVariable Long clienteId) {
        rutinaService.asignar(authentication.getName(), rutinaId, clienteId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{rutinaId}/asignar/{clienteId}")
    @PreAuthorize("hasRole('ENTRENADOR')")
    public ResponseEntity<Void> quitar(
            Authentication authentication,
            @PathVariable Long rutinaId,
            @PathVariable Long clienteId) {
        rutinaService.quitar(authentication.getName(), rutinaId, clienteId);
        return ResponseEntity.noContent().build();
    }
}
