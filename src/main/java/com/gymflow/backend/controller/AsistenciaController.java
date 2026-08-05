package com.gymflow.backend.controller;

import com.gymflow.backend.dto.AdminMarcarAsistenciaRequestDTO;
import com.gymflow.backend.dto.AsistenciaAcompanadoDTO;
import com.gymflow.backend.dto.AsistenciaResponseDTO;
import com.gymflow.backend.dto.AsistenciaSemanaDTO;
import com.gymflow.backend.dto.CarnetResponseDTO;
import com.gymflow.backend.dto.KioskCheckInRequestDTO;
import com.gymflow.backend.service.AsistenciaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/asistencias")
@RequiredArgsConstructor
public class AsistenciaController {

    private final AsistenciaService asistenciaService;

    @PostMapping("/mi")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<AsistenciaResponseDTO> marcarMi(Authentication authentication) {
        // Identidad del JWT (authentication.getName()), nunca un body: el
        // check-in self-service no acepta payload (regla anti mass-assignment,
        // método decidido por el servidor).
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(asistenciaService.marcarMi(authentication.getName()));
    }

    /**
     * Check-in del kiosco de recepción (Fase 5, endpoint #2). Única excepción
     * al "todo autenticado" del proyecto: permitAll() en SecurityConfig +
     * X-Kiosk-Key validada acá con BCrypt (KioskKeyInvalidaException → 401) +
     * KioskRateLimitFilter (doble cuenta 30/min por dispositivo y 100/min por
     * IP, fail-closed 503). Sin @PreAuthorize: no hay rol — la credencial es
     * del dispositivo, no de la persona que escanea.
     */
    @PostMapping("/kiosk")
    public ResponseEntity<AsistenciaResponseDTO> marcarKiosk(
            @Valid @RequestBody KioskCheckInRequestDTO request,
            @RequestHeader(value = "X-Kiosk-Key", required = false) String apiKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(asistenciaService.marcarKiosk(request.getCodigo(), apiKey));
    }

    @GetMapping("/mi/semana")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<AsistenciaSemanaDTO> semana(Authentication authentication) {
        return ResponseEntity.ok(asistenciaService.semana(authentication.getName()));
    }

    @GetMapping("/mi/carnet")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<CarnetResponseDTO> miCarnet(Authentication authentication) {
        // Identidad del JWT (authentication.getName()), nunca un body: el
        // carnet es del usuario autenticado y el código nunca sale del server
        // por otro canal.
        return ResponseEntity.ok(asistenciaService.miCarnet(authentication.getName()));
    }

    // ---- Fase 5, P5: semana del ENTRENADOR + control del ADMIN ----

    @GetMapping("/acompanados/semana")
    @PreAuthorize("hasRole('ENTRENADOR')")
    public ResponseEntity<List<AsistenciaAcompanadoDTO>> semanaAcompanados(Authentication authentication) {
        return ResponseEntity.ok(asistenciaService.semanaAcompanados(authentication.getName()));
    }

    @PostMapping("/admin/marcar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AsistenciaResponseDTO> adminMarcar(
            @Valid @RequestBody AdminMarcarAsistenciaRequestDTO request) {
        // Solo usuarioId en el body: el método (ADMIN) lo decide el servidor
        // y el ADMIN autenticado no necesita identidad acá (anti mass-assignment).
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(asistenciaService.adminMarcar(request.getUsuarioId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> desmarcar(@PathVariable Long id) {
        asistenciaService.desmarcar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin/historial")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AsistenciaResponseDTO>> historial(
            @RequestParam Long usuarioId,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(asistenciaService.historial(usuarioId, pageable));
    }
}
