package com.gymflow.backend.security.filter;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Filtro del kiosco (Fase 5, POST /api/asistencias/kiosk): doble cuenta
 * 30/min por dispositivo (huella de key + IP) y 100/min por IP; fail-closed
 * 503 si Redis no está (mismo criterio que Login/ChatRateLimitFilter).
 * La huella SHA-256 del header X-Kiosk-Key es lo que va al label de Redis
 * (nunca el raw), verificable acá.
 */
class KioskRateLimitFilterTest {

    private static final String SHA256_DE_LLAVE_SECRETA =
            "5597ad5112c641e946851626c1a2e493d1e7057388db37a9bf51fecedfe85f8b";

    @Test
    void noFiltraFueraDeLaRutaDelKiosk() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        KioskRateLimitFilter filter = new KioskRateLimitFilter(redisTemplate);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/asistencias/mi");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        // no pasó por doFilterInternal: no se toca Redis para rutas ajenas
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void bloqueaCon429CuandoElDispositivoSuperaTreintaIntentosPorMinuto() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // primer increment → 31 (excede 30); segundo → 1
        when(valueOperations.increment(anyString())).thenReturn(31L, 1L);
        KioskRateLimitFilter filter = new KioskRateLimitFilter(redisTemplate);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/asistencias/kiosk");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Kiosk-Key", "llave-secreta");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void bloqueaCon429CuandoLaIpSuperaCienIntentosPorMinuto() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // el conteo del dispositivo puede estar bajo; el global por IP es el que
        // supera el tope (101 > 100)
        when(valueOperations.increment(anyString())).thenReturn(2L, 101L);
        KioskRateLimitFilter filter = new KioskRateLimitFilter(redisTemplate);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/asistencias/kiosk");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Kiosk-Key", "llave-secreta");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void bloqueaCon503CuandoRedisNoEstaDisponible() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis caído"));
        KioskRateLimitFilter filter = new KioskRateLimitFilter(redisTemplate);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/asistencias/kiosk");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(503);
    }

    @Test
    void usaLaHuellaSha256EnElLabelDeRedisYNuncaLaKeyCruda() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        KioskRateLimitFilter filter = new KioskRateLimitFilter(redisTemplate);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/asistencias/kiosk");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Kiosk-Key", "llave-secreta");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        verify(valueOperations).increment("ratelimit:kiosk:" + SHA256_DE_LLAVE_SECRETA + ":127.0.0.1");
        verify(valueOperations).increment("ratelimit:kiosk-ip:127.0.0.1");
    }
}