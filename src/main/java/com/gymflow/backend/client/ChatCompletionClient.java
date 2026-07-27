package com.gymflow.backend.client;

public interface ChatCompletionClient {

    String completar(String instruccionesDelSistema, String mensajeDelUsuario);
}
