package com.gymflow.backend.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
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

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitFilter.class);

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

        Long intentos;
        try {
            intentos = redisTemplate.opsForValue().increment(key);
            if (intentos != null && intentos == 1L) {
                // Primer intento en esta ventana: fija la expiración del contador
                redisTemplate.expire(key, VENTANA);
            }
        } catch (RedisConnectionFailureException e) {
            // Política FAIL-CLOSED deliberada para login/registro: un control
            // de seguridad (rate limiting) que se cae en silencio ante un
            // fallo de infraestructura es peor que un login temporalmente no
            // disponible. Se devuelve 503 (no 429) para que sea claramente
            // distinguible de "superaste el límite" en logs y en el cliente.
            //
            // OJO: esta política solo es defendible si Redis en sí está
            // protegido (requirepass + sin exposición pública del puerto).
            // Sin eso, cualquiera que pueda tumbar Redis tumba el login de
            // todo el sistema. Ver hallazgo 1.1 del THREAT_MODEL.md.
            log.error("Redis no disponible para rate limiting de auth: {}", e.getMessage());
            response.setStatus(503); // Service Unavailable
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"message\":\"Servicio temporalmente no disponible. Intenta de nuevo en un momento.\"}"
            );
            return;
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
        // LIMITACIÓN CONOCIDA (hallazgo 2.2 del THREAT_MODEL.md): este método
        // confía en el header X-Forwarded-For tal cual llega, sin validar que
        // el request realmente pasó por el proxy de Railway. Un cliente que
        // hable directo con el backend (bypaseando el proxy) puede mandar
        // cualquier valor acá y rotar la IP "vista" en cada intento,
        // esquivando el rate limit por completo.
        //
        // La mitigación correcta requiere confiar en este header SOLO cuando
        // la conexión entrante viene de la red interna/IP conocida del proxy
        // de Railway (no siempre expuesta o estática en PaaS), por lo que no
        // se resuelve acá de forma genérica. Mientras tanto: asegurar que el
        // backend NO sea alcanzable directamente desde internet (solo vía el
        // proxy/red interna de Railway) reduce el riesgo de bypass, aunque no
        // lo elimina si alguien logra hablarle directo al contenedor.
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
