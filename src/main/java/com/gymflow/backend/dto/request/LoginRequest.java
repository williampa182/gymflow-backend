package com.gymflow.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email inválido")
    private String email;

    // Límite agregado (security-deep-dive §3, GLM-5.2): sin esto, un
    // password arbitrariamente largo llega hasta BCrypt en un endpoint
    // público, habilitando DoS de CPU trivial. RegisterRequest ya tenía
    // este límite; login estaba asimétricamente desprotegido.
    @NotBlank(message = "La contraseña es obligatoria")
    @Size(max = 128, message = "La contraseña no puede exceder 128 caracteres")
    private String password;
}