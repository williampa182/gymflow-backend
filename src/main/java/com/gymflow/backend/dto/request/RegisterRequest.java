package com.gymflow.backend.dto.request;

import com.gymflow.backend.validation.NotCommonPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO de registro público. Campo `rol` OPCIONAL y limitado a la whitelist
 * CLIENTE/ENTRENADOR (Fase 2, 2026-08-02): el usuario puede pedir nacer
 * como entrenador, nunca como ADMIN.
 *
 * Regla de seguridad: este DTO NO valida `rol` con @Pattern a propósito.
 * Cualquier valor no whitelistado (ausente, vacío, "ADMIN" o desconocido)
 * se degrada a CLIENTE en AuthService — es el fix de escalada de privilegios
 * §7.0 del security deep dive, cuyo test de regresión espera 200 + CLIENTE
 * cuando el body manda "rol":"ADMIN". Forzar la validación convertiría esa
 * respuesta en 400 y rompería el contrato que AuthRegisterPrivilegeEscalationRegressionTest verifica.
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

    /** Whitelist para auto-registro: "CLIENTE" o "ENTRENADOR"; cualquier otro valor queda de lado del service. */
    private String rol;
}