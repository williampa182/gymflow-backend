package com.gymflow.backend.exception;

/**
 * Credencial del kiosco inválida o ausente (Fase 5). Se lanza desde el
 * controller cuando el header X-Kiosk-Key no matchea la BCrypt de
 * KioscoConfigService; GlobalExceptionHandler la mapea a 401 (un 401 de
 * credencial es distinto del 403 de rol y del 400 de enumeración).
 */
public class KioskKeyInvalidaException extends RuntimeException {

    public KioskKeyInvalidaException() {
        super("Credencial de kiosco inválida");
    }
}