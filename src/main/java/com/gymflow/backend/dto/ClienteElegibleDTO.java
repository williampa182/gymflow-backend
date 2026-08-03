package com.gymflow.backend.dto;

import com.gymflow.backend.model.AsignacionEntrenador;
import com.gymflow.backend.model.Usuario;

/**
 * Cliente que el entrenador puede acompañar (plan activo con
 * incluyeEntrenadorPersonal) o que ya acompaña. Nunca expone email:
 * el cliente se identifica por id + nombre, y solo para los clientes que
 * califican — nunca lista usuarios a los que el entrenador no deba ver.
 */
public record ClienteElegibleDTO(
        Long id,
        String nombre,
        boolean yaAcompaño,
        Long asignacionId
) {
    public static ClienteElegibleDTO from(Usuario cliente, AsignacionEntrenador asignacion) {
        return new ClienteElegibleDTO(
                cliente.getId(),
                cliente.getNombre(),
                asignacion != null,
                asignacion != null ? asignacion.getId() : null
        );
    }
}
