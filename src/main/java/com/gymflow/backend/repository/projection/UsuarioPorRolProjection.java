package com.gymflow.backend.repository.projection;

import com.gymflow.backend.model.enums.Rol;

public interface UsuarioPorRolProjection {
    Rol getRol();
    long getCantidad();
}
