package com.gymflow.backend.security.filter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica end-to-end que RemoteIpValve (fix hallazgo 2.2 del THREAT_MODEL.md)
 * resuelve la IP del cliente correctamente contra la matriz de escenarios de
 * collab/propuestas/2026-07-24-propuesta-fix-threat-model-2.2-v2-tomcat-native.md
 *
 * Observa el comportamiento en vez de un endpoint de diagnóstico (el
 * DiagnosticHeaderController se eliminó el 2026-08-06): LoginRateLimitFilter
 * lleva un bucket de rate limit por IP YA RESUELTA por el valve
 * ("ratelimit:auth:login:<ip>"), así que el estado del bucket expone qué IP
 * resolvió Tomcat tras procesar X-Forwarded-For.
 *
 * Matriz: un X-Forwarded-For inyectado no debe crear buckets nuevos ni
 * evadir el rate limit; la resolución es rightmost-no-interno.
 *
 * En estos tests, la conexión TCP real del cliente HTTP de prueba es
 * 127.0.0.1 (loopback) — que matchea internal-proxies, por lo que el valve
 * SIEMPRE confía y procesa X-Forwarded-For. Esto simula correctamente el
 * caso "request llega a través del proxy interno de Railway".
 *
 * Nota: cada test usa IPs X-Forwarded-For únicas para que los buckets de
 * Redis (ventana de 1 minuto) no se contaminen entre tests ni con la IP
 * loopback real de otras clases de integración.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(properties = "app.security.auth-rate-limit.max-per-minute=3")
class RemoteIpValveIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    /** Login con credenciales inválidas: 401 dentro del límite, 429 al agotar el bucket de la IP resuelta. */
    private int loginConXff(String xff) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (xff != null) {
            headers.set("X-Forwarded-For", xff);
        }
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>("{\"email\":\"x@y.z\",\"password\":\"incorrecta\"}", headers),
                String.class);
        return response.getStatusCode().value();
    }

    private void agotarBucketDe(String xff) {
        for (int i = 0; i < 3; i++) {
            assertThat(loginConXff(xff)).isEqualTo(401);
        }
    }

    @Test
    void headerSimpleConIpPublica_tieneBucketPropioYAgota() {
        // Escenario 1 de la propuesta: tráfico legítimo vía proxy de Railway.
        // El valve resuelve 203.0.113.50 → su bucket se agota y responde 429.
        agotarBucketDe("203.0.113.50");
        assertThat(loginConXff("203.0.113.50")).isEqualTo(429);
    }

    @Test
    void spoofingLeftmost_noCreaBucketNuevoNiEvadeElLimite() {
        // Escenario 2: atacante antepone una IP falsa. RemoteIpValve lee de
        // derecha a izquierda: toma 203.0.113.51 (rightmost, no interno) y
        // nunca evalúa 1.1.1.1 → el spoof comparte el bucket de la IP real
        // (sigue bloqueado) y no abre un bucket paralelo por 1.1.1.1.
        agotarBucketDe("203.0.113.51");
        assertThat(loginConXff("1.1.1.1, 203.0.113.51")).isEqualTo(429);
        assertThat(loginConXff("203.0.113.55")).isEqualTo(401);
    }

    @Test
    void multiHopInyectado_resuelveElUltimoValorNoInterno() {
        // Escenario 3: múltiples IPs falsas antepuestas, incluso otras
        // públicas. Sigue resolviendo el rightmost no interno (203.0.113.52)
        // y 8.8.8.8 queda ignorada (su bucket no se toca).
        agotarBucketDe("203.0.113.52");
        assertThat(loginConXff("8.8.8.8, 1.1.1.1, 203.0.113.52")).isEqualTo(429);
        assertThat(loginConXff("8.8.8.8")).isEqualTo(401);
    }

    @Test
    void saltoInternoAlFinal_descartaHopsInternosYResuelveElPublico() {
        // Simula un hop interno adicional agregado DESPUÉS de la IP real del
        // cliente en la cadena. El valve debe descartar 10.0.0.5 (interno) y
        // seguir a la izquierda hasta 203.0.113.54 → mismo bucket, bloqueado.
        agotarBucketDe("203.0.113.54");
        assertThat(loginConXff("203.0.113.54, 10.0.0.5")).isEqualTo(429);
    }
}
