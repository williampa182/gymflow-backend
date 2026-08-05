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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Rate limiting del kiosco de recepción (Fase 5, POST /api/asistencias/kiosk),
 * con la misma política que LoginRateLimitFilter: ventana fija de 1 minuto y
 * FAIL-CLOSED 503 si Redis no está (un control de seguridad caído en silencio
 * es peor que un ingreso temporalmente bloqueado).
 *
 * Doble cuenta (spec Fase 5):
 *  - ratelimit:kiosk:&lt;huellaDeClave&gt;:&lt;ip&gt; → 30/min. Límite real del
 *    dispositivo: si la misma clave es usada por muchos kioscos detrás del
 *    mismo NAT, el abuso del dispositivo queda acotado.
 *  - ratelimit:kiosk-ip:&lt;ip&gt; → 100/min: anti-abuso global, mitiga la IP
 *    compartida con el admin en Railway (la IP ya la resolvió RemoteIpValve).
 *
 * La huella es SHA-256 del header X-Kiosk-Key (no el raw): el label en Redis
 * no revela la credencial. La VALIDACIÓN criptográfica de la key NO vive acá —
 * vive en el controller (KioscoConfigService.validar → 401), porque el filtro
 * no debe gastar un BCrypt por request ni decidir lógica de negocio.
 */
@Component
@RequiredArgsConstructor
public class KioskRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(KioskRateLimitFilter.class);

    private static final int MAX_POR_DISPOSITIVO = 30;
    private static final int MAX_POR_IP = 100;
    private static final Duration VENTANA = Duration.ofMinutes(1);

    private final StringRedisTemplate redisTemplate;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !"/api/asistencias/kiosk".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String ip = request.getRemoteAddr();
        String rawKey = request.getHeader("X-Kiosk-Key");
        String huella = huella(rawKey);

        String claveDispositivo = "ratelimit:kiosk:" + huella + ":" + ip;
        String claveIp = "ratelimit:kiosk-ip:" + ip;

        try {
            Long porDispositivo = incrementar(claveDispositivo);
            Long porIp = incrementar(claveIp);
            if (porDispositivo != null && porDispositivo > MAX_POR_DISPOSITIVO) {
                escribir(response, 429, "Demasiados intentos de kiosco. Espera un minuto antes de volver a intentarlo.");
                return;
            }
            if (porIp != null && porIp > MAX_POR_IP) {
                escribir(response, 429, "Demasiados intentos de kiosco. Espera un minuto antes de volver a intentarlo.");
                return;
            }
        } catch (RedisConnectionFailureException e) {
            log.error("Redis no disponible para rate limiting de kiosco: {}", e.getMessage());
            escribir(response, 503, "Servicio temporalmente no disponible. Intenta de nuevo en un momento.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Long incrementar(String clave) {
        Long conteo = redisTemplate.opsForValue().increment(clave);
        if (conteo != null && conteo == 1L) {
            // Primer intento en esta ventana: fija la expiración del contador.
            redisTemplate.expire(clave, VENTANA);
        }
        return conteo;
    }

    /**
     * SHA-256 del raw de la key (o del literal vacío si no viene header).
     * Para metadata de Redis solo; jamás se loguea la clave en sí.
     */
    private String huella(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String normalizada = rawKey == null ? "" : rawKey.trim();
            return HexFormat.of().formatHex(digest.digest(normalizada.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 está garantizado en la JVM; si no existiera (imposible),
            // estaríamos ante un problema mayor que el identificador del bucket.
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private void escribir(HttpServletResponse response, int status, String mensaje) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + mensaje + "\"}");
    }
}