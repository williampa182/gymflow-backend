package com.gymflow.backend.repository.projection;

import com.gymflow.backend.model.enums.EstadoSuscripcion;

public interface SuscripcionPorEstadoProjection {
    EstadoSuscripcion getEstado();
    long getCantidad();
}
