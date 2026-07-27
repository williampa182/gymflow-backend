package com.gymflow.backend.client;

/**
 * Fallo al comunicarse con el proveedor de LLM (red, error HTTP, respuesta
 * inesperada). Separada de RestClientResponseException para que el resto
 * del código (controlador, tests) no dependa de un tipo específico de
 * cliente HTTP — mismo motivo por el que ChatService solo conoce
 * ChatCompletionClient, no RestClient.
 */
public class ChatCompletionException extends RuntimeException {

    public ChatCompletionException(String message, Throwable cause) {
        super(message, cause);
    }
}
