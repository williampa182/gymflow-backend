package com.gymflow.backend.controller;

import com.gymflow.backend.dto.PlanRequestDTO;
import com.gymflow.backend.dto.PlanResponseDTO;
import com.gymflow.backend.service.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/planes")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlanResponseDTO> crear(
            @Valid @RequestBody PlanRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.crear(request));
    }

    @GetMapping
    public ResponseEntity<List<PlanResponseDTO>> listar(
            @RequestParam(required = false) Boolean activo) {
        return ResponseEntity.ok(planService.listar(activo));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanResponseDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(planService.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlanResponseDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody PlanRequestDTO request) {
        return ResponseEntity.ok(planService.actualizar(id, request));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlanResponseDTO> cambiarEstado(
            @PathVariable Long id,
            @RequestParam boolean activo) {
        return ResponseEntity.ok(planService.cambiarEstado(id, activo));
    }
}