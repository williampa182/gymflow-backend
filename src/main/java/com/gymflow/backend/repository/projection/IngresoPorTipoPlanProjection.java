package com.gymflow.backend.repository.projection;

import com.gymflow.backend.model.enums.TipoPlan;

import java.math.BigDecimal;

public interface IngresoPorTipoPlanProjection {
    TipoPlan getTipoPlan();
    BigDecimal getIngresoEstimado();
    long getCantidadSuscripciones();
}
