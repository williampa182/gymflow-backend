package com.gymflow.backend.controller;

import com.gymflow.backend.dto.request.LoginRequest;
import com.gymflow.backend.dto.request.RegisterRequest;
import com.gymflow.backend.dto.response.AuthResponse;
import com.gymflow.backend.dto.response.RegistroEstadoDTO;
import com.gymflow.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> registrar(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.registrar(request));
    }

    // Fase 2: estado del auto-registro (bootstrap del primer admin). Público:
    // el formulario de registro lo consulta para mostrar si el próximo
    // registro nacerá ADMIN sin loguearse. No revela datos de usuarios.
    @GetMapping("/registro-estado")
    public ResponseEntity<RegistroEstadoDTO> estadoRegistro() {
        return ResponseEntity.ok(
                new RegistroEstadoDTO(authService.elPrimerRegistroSeraAdmin()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}