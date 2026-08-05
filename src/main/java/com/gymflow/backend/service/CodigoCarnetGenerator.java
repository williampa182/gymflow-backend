package com.gymflow.backend.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.function.Predicate;

/**
 * Generador de códigos de carnet (Fase 5). ALFABETO compartido con el literal
 * del backfill de la migración 005_codigo_carnet_y_asistencias.sql — debe
 * coincidir SIEMPRE: 24 letras (A-Z sin I ni O) + 8 dígitos (2-9) = 32
 * símbolos, sin 0/O/1/I para evitar ambigüedad visual.
 *
 * Gating estricto: {@link #generarUnico} reintenta hasta MAX_REINTENTOS si el
 * código generado está ocupado (predicado provisto por el caller, que consulta
 * el índice único uq_usuarios_codigo_carnet). Si agota los reintentos lanza
 * excepción — nunca se guarda un usuario sin código.
 */
@Component
public class CodigoCarnetGenerator {

    public static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    public static final int LONGITUD_MIN = 6;
    public static final int LONGITUD_MAX = 8;
    public static final int MAX_REINTENTOS = 10;

    private final SecureRandom random = new SecureRandom();

    public String generar() {
        int longitud = LONGITUD_MIN + random.nextInt(LONGITUD_MAX - LONGITUD_MIN + 1);
        StringBuilder sb = new StringBuilder(longitud);
        for (int i = 0; i < longitud; i++) {
            sb.append(ALFABETO.charAt(random.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    public String generarUnico(Predicate<String> ocupado) {
        for (int i = 0; i < MAX_REINTENTOS; i++) {
            String codigo = generar();
            if (!ocupado.test(codigo)) {
                return codigo;
            }
        }
        throw new RuntimeException("No se pudo generar un codigo de carnet unico");
    }
}
