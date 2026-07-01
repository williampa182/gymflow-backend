package com.gymflow.backend.controller;

import com.gymflow.backend.dto.SuscripcionRequestDTO;
import com.gymflow.backend.dto.SuscripcionResponseDTO;
import com.gymflow.backend.model.enums.EstadoSuscripcion;
import com.gymflow.backend.service.SuscripcionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suscripciones")
@RequiredArgsConstructor
public class SuscripcionController {

    private final SuscripcionService suscripcionService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SuscripcionResponseDTO> crear(
            @Valid @RequestBody SuscripcionRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(suscripcionService.crear(request));
    }

    @GetMapping("/usuario/{usuarioId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SuscripcionResponseDTO>> listarPorUsuario(
            @PathVariable Long usuarioId) {
        return ResponseEntity.ok(suscripcionService.listarPorUsuario(usuarioId));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SuscripcionResponseDTO>> listarPorEstado(
            @RequestParam(required = false) EstadoSuscripcion estado) {
        return ResponseEntity.ok(suscripcionService.listarPorEstado(estado));
    }

    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SuscripcionResponseDTO> cancelar(@PathVariable Long id) {
        return ResponseEntity.ok(suscripcionService.cancelar(id));
    }
}