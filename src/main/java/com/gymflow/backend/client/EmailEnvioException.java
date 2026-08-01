package com.gymflow.backend.client;

/**
 * Fallo al comunicarse con el proveedor de email (red, error HTTP, respuesta
 * inesperada). Separada de RestClientResponseException para que el resto del
 * código (servicio de notificaciones, tests) no dependa de un tipo específico
 * de cliente HTTP — mismo motivo por el que NotificacionVencimientoService solo
 * conoce EmailClient, no RestClient.
 */
public class EmailEnvioException extends RuntimeException {

    public EmailEnvioException(String message, Throwable cause) {
        super(message, cause);
    }
}
