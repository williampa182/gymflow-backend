package com.gymflow.backend.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Asigna un ID de correlación único a cada request entrante (o reutiliza el
 * que venga en el header {@code X-Request-ID}, por si el frontend/proxy ya
 * generó uno) y lo pone en el MDC de SLF4J bajo la clave "correlationId".
 *
 * Esto hace que TODOS los logs emitidos durante el procesamiento de un
 * request (controller, service, filtros de seguridad, excepciones) queden
 * taggeados con el mismo ID, permitiendo reconstruir el flujo completo de
 * una request específica en los logs — clave para depurar en producción
 * cuando hay múltiples requests concurrentes entremezclados en la salida.
 *
 * Se ejecuta antes que cualquier otro filtro (Ordered.HIGHEST_PRECEDENCE)
 * para que el ID esté disponible incluso en los filtros de seguridad
 * (JwtAuthFilter, LoginRateLimitFilter).
 *
 * El ID también se devuelve en la respuesta (header X-Request-ID) para que
 * el cliente pueda reportarlo si necesita soporte ("decime el X-Request-ID
 * que te devolvió para buscar el log exacto").
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Request-ID";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        try {
            MDC.put(MDC_KEY, correlationId);
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            // Crítico: limpiar el MDC al terminar. Los hilos se reutilizan
            // (thread pool de Tomcat), así que si no se limpia, el próximo
            // request procesado por ese mismo hilo heredaría el ID viejo.
            MDC.remove(MDC_KEY);
        }
    }
}
