package com.gymflow.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO de actualización de datos de contacto por el ADMIN (recepción).
 * Todo opcional y nullable: ausente o vacío limpia el campo. Sin `rol`,
 * sin documento, sin id (anti mass-assignment, igual que
 * CrearClienteRequest).
 */
@Data
public class ActualizarContactoRequest {

    @Email(message = "Formato de email inválido")
    private String email;

    @Size(max = 30, message = "El teléfono supera el máximo permitido")
    private String telefono;
}
