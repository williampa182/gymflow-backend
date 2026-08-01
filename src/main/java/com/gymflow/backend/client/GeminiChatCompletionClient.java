package com.gymflow.backend.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    private static final Logger log = LoggerFactory.getLogger(GeminiChatCompletionClient.class);

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
                .requestFactory(crearRequestFactory())
                .build();
    }

    /**
     * Timeouts explícitos (hallazgo M1 de la revisión del 2026-08-01): el
     * default del JDK HttpClient es infinito — un proveedor colgado ocuparía
     * el thread de Tomcat indefinidamente. Connect 3s + read 30s (respuestas
     * LLM de maxOutputTokens 1024 entran de sobra en 30s).
     */
    private static ClientHttpRequestFactory crearRequestFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(30));
        return factory;
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
            long inicio = System.nanoTime();
            GeminiResponse respuesta = restClient.post()
                    .uri(GENERATE_CONTENT_PATH_TEMPLATE.formatted(model))
                    .body(body)
                    .retrieve()
                    .body(GeminiResponse.class);
            long duracionMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);

            String texto = extraerTexto(respuesta);

            // Log de uso sin contenido (blindaje 2026-08-01): solo proveedor,
            // modelo y tokens — sirve para vigilar la cuota del tier gratis
            // sin exponer ni planes ni el mensaje del usuario en los logs.
            if (respuesta != null && respuesta.usageMetadata() != null) {
                log.info("Chat completado: proveedor=gemini, modelo={}, tokens_entrada={}, tokens_salida={}, duracion_ms={}",
                        model,
                        respuesta.usageMetadata().promptTokenCount(),
                        respuesta.usageMetadata().candidatesTokenCount(),
                        duracionMs);
            } else {
                log.info("Chat completado: proveedor=gemini, modelo={}, duracion_ms={}",
                        model, duracionMs);
            }
            return texto;
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
     * necesitamos: el texto del primer candidato y el conteo de tokens de
     * usageMetadata (para el log de uso sin contenido). No mapeamos
     * safetyRatings, finishReason ni ningún otro campo a propósito.
     */
    private record GeminiResponse(List<Candidate> candidates, UsageMetadata usageMetadata) {
        private record Candidate(Content content) {
        }
        private record Content(List<Part> parts) {
        }
        private record Part(String text) {
        }
        private record UsageMetadata(Integer promptTokenCount, Integer candidatesTokenCount) {
        }
    }
}
