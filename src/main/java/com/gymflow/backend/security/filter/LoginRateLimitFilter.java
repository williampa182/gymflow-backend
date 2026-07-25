package com.gymflow.backend.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
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
        boolean esLogin = path.equals("/api/auth/login");
        boolean esRegister = path.equals("/api/auth/register");

        if (!esLogin && !esRegister) {
            filterChain.doFilter(request, response);
            return;
        }

        // Fix security-deep-dive §7 (GLM-5.2): antes login y register
        // compartían la misma key de Redis por IP, así que 10 registros
        // basura agotaban también el límite de login para esa IP (y
        // viceversa). Separar por ruta evita ese agotamiento cruzado. El
        // límite (10/min) sigue siendo el mismo para ambas rutas por ahora;
        // ajustar MAX_INTENTOS_POR_MINUTO por ruta es un cambio aparte si
        // se decide que register necesita un límite distinto a login.
        String ip = obtenerIpCliente(request);
        String rutaKey = esLogin ? "login" : "register";
        String key = "ratelimit:auth:" + rutaKey + ":" + ip;

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
        // Fix hallazgo 2.2 del THREAT_MODEL.md (2026-07-24). Antes este
        // método confiaba en el primer elemento de X-Forwarded-For tal cual
        // llegaba, sin validar que el request viniera realmente del proxy de
        // Railway — un atacante podía mandar ese header directo y rotar la
        // IP "vista" en cada intento, evadiendo el rate limit por completo.
        //
        // Ahora RemoteIpValve (server.forward-headers-strategy: native +
        // server.tomcat.internal-proxies en application.yaml) ya resolvió la
        // IP real ANTES de que este filtro vea el request: procesa
        // X-Forwarded-For de derecha a izquierda, descarta los saltos que
        // matcheen la red interna de Railway/Docker, y reemplaza
        // request.getRemoteAddr() con la primera IP no interna encontrada.
        // Ver collab/propuestas/2026-07-24-propuesta-fix-threat-model-2.2-v2-tomcat-native.md
        // para el research y la matriz de escenarios verificada (por qué
        // rightmost y no leftmost, y por qué no se usa X-Envoy-External-Address).
        return request.getRemoteAddr();
    }
}
