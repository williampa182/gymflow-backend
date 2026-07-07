package com.gymflow.backend.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Rate limiting simple para endpoints de autenticación (login/register).
 *
 * Por qué: sin esto, /api/auth/login es un blanco directo para fuerza bruta
 * de contraseñas — es de los huecos de seguridad más comunes en proyectos
 * hechos rápido ("vibe coded"). Usa Redis (ya disponible en el proyecto) para
 * llevar un contador por IP con ventana deslizante fija de 1 minuto.
 *
 * Límite: 10 intentos por IP por minuto a /api/auth/**. Suficiente para un
 * usuario legítimo que se equivoca de contraseña varias veces, demasiado poco
 * para un ataque de fuerza bruta efectivo.
 */
@Component
@RequiredArgsConstructor
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_INTENTOS_POR_MINUTO = 10;
    private static final Duration VENTANA = Duration.ofMinutes(1);

    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        boolean esRutaAuth = path.equals("/api/auth/login") || path.equals("/api/auth/register");

        if (!esRutaAuth) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = obtenerIpCliente(request);
        String key = "ratelimit:auth:" + ip;

        Long intentos = redisTemplate.opsForValue().increment(key);
        if (intentos != null && intentos == 1L) {
            // Primer intento en esta ventana: fija la expiración del contador
            redisTemplate.expire(key, VENTANA);
        }

        if (intentos != null && intentos > MAX_INTENTOS_POR_MINUTO) {
            response.setStatus(429); // Too Many Requests
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"message\":\"Demasiados intentos. Espera un minuto antes de volver a intentarlo.\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String obtenerIpCliente(HttpServletRequest request) {
        // X-Forwarded-For es lo que Railway (y la mayoría de PaaS) usan para
        // pasar la IP real del cliente detrás de su proxy/load balancer.
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
