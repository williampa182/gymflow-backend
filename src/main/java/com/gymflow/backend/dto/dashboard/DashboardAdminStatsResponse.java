package com.gymflow.backend.dto.dashboard;

import com.gymflow.backend.model.enums.EstadoSuscripcion;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.model.enums.TipoPlan;

import java.math.BigDecimal;
import java.util.List;

public record DashboardAdminStatsResponse(
        List<UsuarioPorRolStat> usuariosPorRol,
        List<IngresoPorTipoPlanStat> ingresosPorTipoPlan,
        List<SuscripcionPorEstadoStat> suscripcionesPorEstado
) {

    public record UsuarioPorRolStat(
            Rol rol,
            long cantidad
    ) {}

    public record IngresoPorTipoPlanStat(
            TipoPlan tipoPlan,
            BigDecimal ingresoEstimado,
            long cantidadSuscripciones
    ) {}

    public record SuscripcionPorEstadoStat(
            EstadoSuscripcion estado,
            long cantidad
    ) {}
}
