package com.gymflow.backend.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Adaptador a la API de Resend (envío de emails transaccionales). Mismo
 * contrato y las mismas garantías de aislamiento que los adaptadores de chat
 * (Gemini/Anthropic): único punto del proyecto que conoce HTTP y la API key,
 * NotificacionVencimientoService no sabe qué proveedor hay detrás de la
 * interfaz.
 *
 * La API key de dev es un placeholder (dev-only-placeholder-not-a-real-key,
 * igual que en app.chat): cualquier llamada real con ese valor devuelve
 * 401/403, mapeado a EmailEnvioException por el catch de abajo.
 */
@Component
public class ResendEmailClient implements EmailClient {

    private static final String ENVIAR_EMAIL_PATH = "/emails";

    private final RestClient restClient;

    public ResendEmailClient(
            @Value("${app.email.resend-api-key}") String apiKey,
            @Value("${app.email.resend-base-url:https://api.resend.com}") String baseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public void enviar(EmailPayload payload) {
        Map<String, Object> body = Map.of(
                "from", payload.from(),
                "to", payload.to(),
                "subject", payload.subject(),
                "html", payload.html(),
                "text", payload.text()
        );

        try {
            restClient.post()
                    .uri(ENVIAR_EMAIL_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new EmailEnvioException(
                    "La API de Resend devolvió un error (" + ex.getStatusCode() + ")", ex
            );
        }
    }
}
