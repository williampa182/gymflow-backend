package com.gymflow.backend.service;

import com.gymflow.backend.model.Suscripcion;
import com.gymflow.backend.model.enums.EstadoSuscripcion;

import java.time.LocalDate;

/**
 * Predicado central de vigencia (D1-A): único lugar que decide si una
 * suscripción está vigente o en mora a partir de su estado almacenado y su
 * fecha de fin. "Hoy" siempre viene del Clock de Bogotá (regla 7), nunca de
 * LocalDate.now().
 *
 * - vigente: ACTIVA con fechaFin hoy o futura.
 * - morosa: VENCIDA guardada, o ACTIVA con fechaFin pasada (incluye
 *   fechaFin null como fail-closed: sin fecha no hay vigencia que alegar).
 * - CANCELADA nunca es mora aunque su fecha sea futura.
 */
public final class VigenciaSuscripcion {

    private VigenciaSuscripcion() {
    }

    public static boolean vigente(Suscripcion s, LocalDate hoy) {
        return s != null
                && s.getEstado() == EstadoSuscripcion.ACTIVA
                && s.getFechaFin() != null
                && !s.getFechaFin().isBefore(hoy);
    }

    public static boolean morosa(Suscripcion s, LocalDate hoy) {
        return s != null
                && (s.getEstado() == EstadoSuscripcion.VENCIDA
                    || (s.getEstado() == EstadoSuscripcion.ACTIVA
                        && (s.getFechaFin() == null || s.getFechaFin().isBefore(hoy))));
    }
}
