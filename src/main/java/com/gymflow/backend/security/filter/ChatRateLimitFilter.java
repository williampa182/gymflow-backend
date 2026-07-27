package com.gymflow.backend.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Extiende el patrón de LoginRateLimitFilter para proteger el presupuesto del
 * proveedor LLM. Se conserva la IP ya resuelta por RemoteIpValve y se falla
 * cerrado si Redis no está disponible.
 */
@Component
@RequiredArgsConstructor
public class ChatRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ChatRateLimitFilter.class);
    private static final int MAX_CONSULTAS_POR_MINUTO = 20;
    private static final Duration VENTANA = Duration.ofMinutes(1);

    private final StringRedisTemplate redisTemplate;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !"/api/chat".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String key = "ratelimit:chat:" + request.getRemoteAddr();
        Long consultas;
        try {
            consultas = redisTemplate.opsForValue().increment(key);
            if (consultas != null && consultas == 1L) {
                redisTemplate.expire(key, VENTANA);
            }
        } catch (RedisConnectionFailureException ex) {
            log.error("Redis no disponible para rate limiting de chat: {}", ex.getMessage());
            escribirRespuesta(response, 503, "Servicio temporalmente no disponible. Intenta de nuevo en un momento.");
            return;
        }

        if (consultas != null && consultas > MAX_CONSULTAS_POR_MINUTO) {
            escribirRespuesta(response, 429, "Demasiadas consultas. Espera un minuto antes de volver a intentarlo.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void escribirRespuesta(HttpServletResponse response, int status, String mensaje) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + mensaje + "\"}");
    }
}
