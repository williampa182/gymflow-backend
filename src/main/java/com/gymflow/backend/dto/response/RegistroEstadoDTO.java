package com.gymflow.backend.dto.response;

/**
 * Estado del auto-registro (Fase 2, 2026-08-02): le dice al frontend si el
 * próximo registro nacerá ADMIN (bootstrap del primer admin). El formulario
 * lo usa para mostrar el aviso condicional "primer registro nace admin"
 * solo cuando el sistema no tiene administradores.
 */
public record RegistroEstadoDTO(boolean primerRegistroSeraAdmin) {
}