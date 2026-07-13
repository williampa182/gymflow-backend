package com.gymflow.backend.dto.request;

import com.gymflow.backend.validation.NotCommonPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO de registro público. A PROPÓSITO no incluye el campo `rol`:
 * un endpoint de registro público jamás debe permitir que el cliente
 * elija su propio rol (era una vulnerabilidad de escalada de
 * privilegios confirmada — cualquiera podía registrarse como ADMIN
 * mandando "rol":"ADMIN" en el body). Todo usuario que se registra
 * por acá queda como CLIENTE; promoverlo a ENTRENADOR/ADMIN es una
 * acción de administración separada (PATCH /api/usuarios/{id}/rol,
 * si se implementa) y no algo que el propio usuario controla.
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email inválido")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 12, max = 128, message = "La contraseña debe tener entre 12 y 128 caracteres")
    @NotCommonPassword
    private String password;
}