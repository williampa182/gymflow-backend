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
 * Único punto del proyecto que conoce HTTP y la API key de Anthropic. El
 * resto del flujo del chatbot (ChatService, ChatController) solo conoce la
 * interfaz {@link ChatCompletionClient} — así el prompt injection queda
 * acotado a "el LLM responde cosas raras en el campo `respuesta`", nunca a
 * "el LLM puede desencadenar una llamada HTTP distinta o tocar la base de
 * datos": este adaptador no expone ninguna otra operación.
 *
 * La API key vive solo en el backend, vía {@code ANTHROPIC_API_KEY}. Mismo
 * patrón que JWT_SECRET/REDIS_PASSWORD en application.yaml: placeholder de
 * dev por defecto (nunca funcional contra la API real), obligatorio
 * sobreescribir en cualquier entorno real. Sin este placeholder, cada test
 * @SpringBootTest del proyecto (no solo los del chatbot) rompería al cargar
 * el ApplicationContext — este proyecto no tiene application-test.yaml ni
 * perfil global de test, cada test fija sus propias properties puntuales
 * con @TestPropertySource. Si la key es el placeholder (o cualquier otra
 * inválida), la falla ocurre en la llamada real (401 de Anthropic →
 * ChatCompletionException → 503), no en el arranque.
 *
 * Activo solo cuando app.chat.provider=anthropic. Mientras el proyecto usa
 * Gemini (ver GeminiChatCompletionClient) por costo — decisión de William,
 * 2026-07-26 — este bean no se crea, para no chocar con dos beans de
 * ChatCompletionClient en el mismo contexto.
 */
@Component
@ConditionalOnProperty(name = "app.chat.provider", havingValue = "anthropic")
public class AnthropicChatCompletionClient implements ChatCompletionClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String MESSAGES_PATH = "/v1/messages";

    private final RestClient restClient;
    private final String model;
    private final int maxTokens;

    public AnthropicChatCompletionClient(
            @Value("${app.chat.anthropic-api-key}") String apiKey,
            @Value("${app.chat.base-url:https://api.anthropic.com}") String baseUrl,
            @Value("${app.chat.model:claude-haiku-4-5-20251001}") String model,
            @Value("${app.chat.max-tokens:512}") int maxTokens
    ) {
        this.model = model;
        this.maxTokens = maxTokens;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .defaultHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String completar(String instruccionesDelSistema, String mensajeDelUsuario) {
        // Roles separados a nivel de API (system vs messages[].user), nunca
        // concatenación de strings: es la mitigación central de prompt
        // injection acordada para este endpoint. El mensaje del usuario
        // viaja SOLO en el rol "user"; nunca se mezcla con el bloque
        // "system" ni se interpola dentro de él.
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "system", instruccionesDelSistema,
                "messages", List.of(
                        Map.of("role", "user", "content", mensajeDelUsuario)
                )
        );

        try {
            AnthropicResponse respuesta = restClient.post()
                    .uri(MESSAGES_PATH)
                    .body(body)
                    .retrieve()
                    .body(AnthropicResponse.class);

            return extraerTexto(respuesta);
        } catch (RestClientResponseException ex) {
            throw new ChatCompletionException(
                    "La API de Anthropic devolvió un error (" + ex.getStatusCode() + ")", ex
            );
        }
    }

    private String extraerTexto(AnthropicResponse respuesta) {
        if (respuesta == null || respuesta.content() == null || respuesta.content().isEmpty()) {
            throw new ChatCompletionException("La API de Anthropic devolvió una respuesta vacía", null);
        }
        return respuesta.content().stream()
                .filter(bloque -> "text".equals(bloque.type()))
                .map(AnthropicResponse.ContentBlock::text)
                .reduce("", String::concat);
    }

    /**
     * Subconjunto mínimo del esquema de respuesta de /v1/messages que
     * necesitamos: solo el texto. No mapeamos tool_use ni ningún otro tipo
     * de bloque a propósito — este cliente no soporta tool calling.
     */
    private record AnthropicResponse(List<ContentBlock> content) {
        private record ContentBlock(String type, String text) {
        }
    }
}
