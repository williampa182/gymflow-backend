package com.gymflow.backend.dto;

import com.gymflow.backend.model.Usuario;

/**
 * Cliente que tiene asignada una rutina, en la vista del ENTRENADOR
 * (GET /api/rutinas). Nunca expone email — mismo criterio que
 * ClienteElegibleDTO: el cliente se identifica por id + nombre.
 */
public record ClienteAsignadoDTO(
        Long id,
        String nombre
) {
    public static ClienteAsignadoDTO from(Usuario cliente) {
        return new ClienteAsignadoDTO(cliente.getId(), cliente.getNombre());
    }
}
