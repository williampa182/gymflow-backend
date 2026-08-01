package com.gymflow.backend.controller;

import com.gymflow.backend.client.ChatCompletionException;
import com.gymflow.backend.dto.request.ChatRequest;
import com.gymflow.backend.dto.response.ChatResponse;
import com.gymflow.backend.service.ChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    @InjectMocks
    private ChatController chatController;

    @Test
    void responder_devuelveSoloElTextoDeRespuestaDelServicio() {
        when(chatService.responder("¿Qué planes están disponibles?"))
                .thenReturn("Tenemos un plan mensual activo.");

        ChatResponse respuesta = chatController.responder(new ChatRequest("¿Qué planes están disponibles?"));

        assertThat(respuesta.respuesta()).isEqualTo("Tenemos un plan mensual activo.");
    }

    @Test
    void responder_lanzaErrorCuandoElChatEstaDeshabilitado() {
        ReflectionTestUtils.setField(chatController, "enabled", Boolean.FALSE);

        assertThatThrownBy(() -> chatController.responder(new ChatRequest("hola")))
                .isInstanceOf(ChatCompletionException.class)
                .hasMessageContaining("deshabilitado");
        verifyNoInteractions(chatService);
    }
}
