package com.gymflow.backend.client;

import java.util.List;

/**
 * Payload mínimo de un email transaccional tal como lo espera la API de
 * Resend (POST /emails): de quién, para quién, asunto, cuerpo HTML y cuerpo
 * en texto plano.
 */
public record EmailPayload(String from, List<String> to, String subject, String html, String text) {
}
