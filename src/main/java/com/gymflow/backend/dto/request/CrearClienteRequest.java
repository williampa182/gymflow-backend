package com.gymflow.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO de creación de clientes por el ADMIN (recepción). Sin campo `rol` a
 * propósito: el service fuerza CLIENTE, igual que RegisterRequest nunca deja
 * nacer ADMIN por auto-registro.
 */
@Data
public class CrearClienteRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @Email(message = "Formato de email inválido")
    private String email;

    @NotBlank(message = "El tipo de documento es obligatorio")
    @Pattern(regexp = "(?i)CC|CE|TI|PEP|PASAPORTE", message = "Tipo de documento inválido")
    private String tipoDocumento;

    @NotBlank(message = "El número de documento es obligatorio")
    @Size(max = 30, message = "El número de documento supera el máximo permitido")
    @Pattern(regexp = "[A-Za-z0-9\\-]{3,30}", message = "El número de documento tiene un formato inválido")
    private String numeroDocumento;

    @Size(max = 30, message = "El teléfono supera el máximo permitido")
    private String telefono;
}
