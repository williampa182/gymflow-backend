package com.gymflow.backend.security.filter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica end-to-end que RemoteIpValve (fix hallazgo 2.2 del THREAT_MODEL.md)
 * resuelve la IP del cliente correctamente contra la matriz de escenarios de
 * collab/propuestas/2026-07-24-propuesta-fix-threat-model-2.2-v2-tomcat-native.md
 *
 * Usa /api/v1/debug/headers (gateado por app.debug-headers.enabled, activado
 * acá solo para este test) para observar qué IP resolvió Tomcat como
 * request.getRemoteAddr() tras pasar por el valve.
 *
 * En estos tests, la conexión TCP real del cliente HTTP de prueba es
 * 127.0.0.1 (loopback) — que matchea internal-proxies, por lo que el valve
 * SIEMPRE confía y procesa X-Forwarded-For. Esto simula correctamente el
 * caso "request llega a través del proxy interno de Railway".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(properties = "app.debug-headers.enabled=true")
class RemoteIpValveIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @SuppressWarnings("unchecked")
    private String remoteAddrPara(String xForwardedFor) {
        HttpHeaders headers = new HttpHeaders();
        if (xForwardedFor != null) {
            headers.set("X-Forwarded-For", xForwardedFor);
        }
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/debug/headers", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return (String) response.getBody().get("remoteAddr");
    }

    @Test
    void sinHeaderXForwardedFor_devuelveIpDeConexionDirecta() {
        // Sin XFF, no hay nada que resolver: queda la IP de la conexión TCP real.
        assertThat(remoteAddrPara(null)).isEqualTo("127.0.0.1");
    }

    @Test
    void headerSimpleConIpPublica_resuelveEsaIp() {
        // Escenario 1 de la propuesta: tráfico legítimo vía proxy de Railway.
        assertThat(remoteAddrPara("203.0.113.50")).isEqualTo("203.0.113.50");
    }

    @Test
    void spoofingLeftmost_ignoraElValorInyectadoAlPrincipio() {
        // Escenario 2: atacante intenta anteponer una IP falsa. RemoteIpValve
        // lee de derecha a izquierda: toma 203.0.113.50 (rightmost, no
        // interno), nunca llega a evaluar 1.1.1.1.
        assertThat(remoteAddrPara("1.1.1.1, 203.0.113.50")).isEqualTo("203.0.113.50");
    }

    @Test
    void multiHopInyectado_resuelveElUltimoValorNoInterno() {
        // Escenario 3: múltiples IPs falsas antepuestas, incluso otras
        // públicas. Sigue resolviendo el rightmost no interno.
        assertThat(remoteAddrPara("8.8.8.8, 1.1.1.1, 203.0.113.50")).isEqualTo("203.0.113.50");
    }

    @Test
    void saltoInternoAlFinal_descartaHopsInternosYResuelveElPublico() {
        // Simula un hop interno adicional agregado DESPUÉS de la IP real del
        // cliente en la cadena. El valve debe descartar 10.0.0.5 (interno) y
        // seguir a la izquierda hasta encontrar 203.0.113.50 (no interno).
        assertThat(remoteAddrPara("203.0.113.50, 10.0.0.5")).isEqualTo("203.0.113.50");
    }
}
