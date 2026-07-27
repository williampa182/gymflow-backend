package com.gymflow.backend.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Adaptador a la API de Gemini (Google), elegido en vez de Anthropic el
 * 2026-07-26 mientras el proyecto no tiene presupuesto — Gemini ofrece un
 * tier gratis genuino (sin tarjeta, sin vencimiento) para los modelos Flash
 * y Flash-Lite. Mismo contrato y las mismas garantías de aislamiento que
 * AnthropicChatCompletionClient: único punto del proyecto que conoce HTTP y
 * la API key, ChatService no sabe qué proveedor hay detrás de la interfaz.
 *
 * Activo solo cuando app.chat.provider=gemini (default del proyecto por
 * ahora). Ver AnthropicChatCompletionClient para volver al otro proveedor
 * el día que haga falta — es cambiar una property, no código.
 *
 * Caveat de privacidad del tier gratis (documentado, no inventado): Google
 * puede usar las requests del tier gratis para mejorar sus modelos. Para
 * este endpoint el dato que viaja es bajo riesgo (nombre/precio de plan +
 * pregunta del usuario), pero queda anotado por si el proyecto cambia de
 * alcance.
 */
@Component
@ConditionalOnProperty(name = "app.chat.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiChatCompletionClient implements ChatCompletionClient {

    private static final String GENERATE_CONTENT_PATH_TEMPLATE = "/v1beta/models/%s:generateContent";

    private final RestClient restClient;
    private final String model;
    private final int maxOutputTokens;

    public GeminiChatCompletionClient(
            @Value("${app.chat.gemini-api-key}") String apiKey,
            @Value("${app.chat.base-url-gemini:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${app.chat.model-gemini:gemini-3.1-flash-lite}") String model,
            @Value("${app.chat.max-tokens:512}") int maxOutputTokens
    ) {
        this.model = model;
        this.maxOutputTokens = maxOutputTokens;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-goog-api-key", apiKey)
                .defaultHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String completar(String instruccionesDelSistema, String mensajeDelUsuario) {
        // Mismo principio que en el adaptador de Anthropic: separación
        // estructural entre instrucciones y mensaje del usuario a nivel de
        // API (system_instruction vs contents), nunca concatenación de
        // strings. Es la mitigación de prompt injection acordada para este
        // endpoint, independiente del proveedor detrás.
        Map<String, Object> body = Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", instruccionesDelSistema))
                ),
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", mensajeDelUsuario)))
                ),
                "generationConfig", Map.of("maxOutputTokens", maxOutputTokens)
        );

        try {
            GeminiResponse respuesta = restClient.post()
                    .uri(GENERATE_CONTENT_PATH_TEMPLATE.formatted(model))
                    .body(body)
                    .retrieve()
                    .body(GeminiResponse.class);

            return extraerTexto(respuesta);
        } catch (RestClientResponseException ex) {
            throw new ChatCompletionException(
                    "La API de Gemini devolvió un error (" + ex.getStatusCode() + ")", ex
            );
        }
    }

    private String extraerTexto(GeminiResponse respuesta) {
        if (respuesta == null || respuesta.candidates() == null || respuesta.candidates().isEmpty()) {
            throw new ChatCompletionException("La API de Gemini devolvió una respuesta vacía", null);
        }
        GeminiResponse.Candidate primero = respuesta.candidates().get(0);
        if (primero.content() == null || primero.content().parts() == null) {
            throw new ChatCompletionException("La API de Gemini devolvió una respuesta sin contenido", null);
        }
        return primero.content().parts().stream()
                .map(GeminiResponse.Part::text)
                .filter(texto -> texto != null)
                .reduce("", String::concat);
    }

    /**
     * Subconjunto mínimo del esquema de respuesta de generateContent que
     * necesitamos: solo el texto del primer candidato. No mapeamos
     * safetyRatings, finishReason ni ningún otro campo a propósito.
     */
    private record GeminiResponse(List<Candidate> candidates) {
        private record Candidate(Content content) {
        }
        private record Content(List<Part> parts) {
        }
        private record Part(String text) {
        }
    }
}
