package com.gymflow.backend.controller;

import com.gymflow.backend.dto.ClienteElegibleDTO;
import com.gymflow.backend.dto.HistorialAcompanamientoDTO;
import com.gymflow.backend.dto.MiEntrenadorDTO;
import com.gymflow.backend.service.EntrenadorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Acompañamiento (Fase 4):
 *   - ENTRENADOR: GET /api/entrenador/clientes-elegibles (listado calculado),
 *     POST /api/entrenador/asignarme/{clienteId}, DELETE /api/entrenador/{asignacionId}
 *   - CLIENTE:    GET /api/entrenador/mio (su acompañante actual),
 *     GET /api/entrenador/mi-historial (todas sus asignaciones, ACTIVAS y canceladas)
 * La identidad siempre sale del JWT; la elegibilidad es una regla derivada
 * (plan ACTIVO con incluyeEntrenadorPersonal), no una decisión manual.
 */
@RestController
@RequestMapping("/api/entrenador")
@RequiredArgsConstructor
public class EntrenadorController {

    private final EntrenadorService entrenadorService;

    @GetMapping("/clientes-elegibles")
    @PreAuthorize("hasRole('ENTRENADOR')")
    public List<ClienteElegibleDTO> listarClientesElegibles(Authentication authentication) {
        return entrenadorService.listarClientesElegibles(authentication.getName());
    }

    @PostMapping("/asignarme/{clienteId}")
    @PreAuthorize("hasRole('ENTRENADOR')")
    public ResponseEntity<Void> asignarme(
            Authentication authentication,
            @PathVariable Long clienteId) {
        entrenadorService.asignarme(authentication.getName(), clienteId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{asignacionId}")
    @PreAuthorize("hasRole('ENTRENADOR')")
    public ResponseEntity<Void> cancelar(
            Authentication authentication,
            @PathVariable Long asignacionId) {
        entrenadorService.cancelar(authentication.getName(), asignacionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/mio")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<MiEntrenadorDTO> miEntrenador(Authentication authentication) {
        return entrenadorService.miEntrenador(authentication.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/mi-historial")
    @PreAuthorize("hasRole('CLIENTE')")
    public List<HistorialAcompanamientoDTO> miHistorial(Authentication authentication) {
        return entrenadorService.miHistorial(authentication.getName());
    }
}
