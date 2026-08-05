package com.gymflow.backend.controller;

import com.gymflow.backend.dto.CarnetResponseDTO;
import com.gymflow.backend.dto.request.CambioRolRequest;
import com.gymflow.backend.dto.UsuarioResponseDTO;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UsuarioResponseDTO>> listar(
            @RequestParam(required = false) Rol rol,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(usuarioService.listarUsuarios(rol, pageable));
    }

    @PatchMapping("/{id}/rol")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponseDTO> cambiarRol(
            @PathVariable Long id,
            @Valid @RequestBody CambioRolRequest request) {
        return ResponseEntity.ok(usuarioService.cambiarRol(id, request.getRol()));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponseDTO> cambiarEstado(
            @PathVariable Long id,
            @RequestParam boolean activo) {
        return ResponseEntity.ok(usuarioService.cambiarEstado(id, activo));
    }

    @GetMapping("/{id}/carnet")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CarnetResponseDTO> carnet(@PathVariable Long id) {
        // Reimpresión: devuelve también el nombre para que el ADMIN vea de
        // quién es el carnet que está imprimiendo.
        return ResponseEntity.ok(usuarioService.obtenerCarnet(id));
    }

    @PostMapping("/{id}/carnet/rotar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CarnetResponseDTO> rotarCarnet(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.rotarCarnet(id));
    }
}
