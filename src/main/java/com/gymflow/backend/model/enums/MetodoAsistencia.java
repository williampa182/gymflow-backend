package com.gymflow.backend.model.enums;

/**
 * Cómo se registró una asistencia (Fase 5). Decidido SIEMPRE por el servidor
 * según la vía de autenticación, jamás por el body (regla anti mass-assignment
 * del repo, THREAT_MODEL §9):
 *   - SELF: el CLIENTE marcó su propia entrada desde su dashboard.
 *   - ADMIN: la marcó un ADMIN (recepción/control).
 *   - KIOSK_CARNET: la marcó el kiosco de recepción con código/QR + apiKey.
 */
public enum MetodoAsistencia {
    SELF,
    ADMIN,
    KIOSK_CARNET
}
