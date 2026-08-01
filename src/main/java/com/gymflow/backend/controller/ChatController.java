package com.gymflow.backend.controller;

import com.gymflow.backend.client.ChatCompletionException;
import com.gymflow.backend.dto.request.ChatRequest;
import com.gymflow.backend.dto.response.ChatResponse;
import com.gymflow.backend.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // Kill-switch del chatbot (blindaje 2026-08-01): permite desactivar el
    // endpoint en prod desde config sin redeploy de código
    // (app.chat.enabled / APP_CHAT_ENABLED). Boolean en vez de boolean para
    // que en tests unitarios (sin contexto Spring) null = habilitado.
    @Value("${app.chat.enabled:true}")
    private Boolean enabled;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ChatResponse responder(@Valid @RequestBody ChatRequest request) {
        if (Boolean.FALSE.equals(enabled)) {
            throw new ChatCompletionException("El chat está deshabilitado", null);
        }
        return new ChatResponse(chatService.responder(request.mensaje()));
    }
}
