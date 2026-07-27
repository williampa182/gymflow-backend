package com.gymflow.backend.controller;

import com.gymflow.backend.dto.request.ChatRequest;
import com.gymflow.backend.dto.response.ChatResponse;
import com.gymflow.backend.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ChatResponse responder(@Valid @RequestBody ChatRequest request) {
        return new ChatResponse(chatService.responder(request.mensaje()));
    }
}
