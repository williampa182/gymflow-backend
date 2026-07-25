package com.gymflow.backend.security.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de regresión para el hallazgo 2.7 del THREAT_MODEL.md: antes del
 * fix, JwtAuthFilter llamaba a jwtUtil.extraerUsername() sin try/catch,
 * así que cualquier header "Authorization: Bearer <basura>" producía una
 * excepción sin manejar en cada request — no solo un riesgo hipotético de
 * DoS, sino un bug real confirmado en runtime (ver THREAT_MODEL.md §7.4).
 *
 * El fix actual envuelve el parseo en try/catch (JwtException,
 * IllegalArgumentException, UsernameNotFoundException) y corta antes por
 * longitud (MAX_JWT_LENGTH = 2048). Este test pega distintas variantes de
 * token malformado contra un endpoint protegido real y verifica que la
 * respuesta sea siempre un 403 limpio — nunca un 500 sin manejar — para
 * detectar si alguien alguna vez saca el try/catch por accidente.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class JwtAuthFilterMalformedTokenRegressionTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private ResponseEntity<String> pegarConAuthHeader(String authHeaderValue) {
        HttpHeaders headers = new HttpHeaders();
        if (authHeaderValue != null) {
            headers.set("Authorization", authHeaderValue);
        }
        return restTemplate.exchange(
                "/api/planes", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    void tokenCompletamenteInvalido_devuelve403LimpioNo500() {
        ResponseEntity<String> response = pegarConAuthHeader("Bearer esto-no-es-un-jwt-valido");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void tokenConSoloDosSegmentos_devuelve403LimpioNo500() {
        ResponseEntity<String> response = pegarConAuthHeader("Bearer header.payload-sin-firma");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void tokenVacioTrasBearer_devuelve403LimpioNo500() {
        ResponseEntity<String> response = pegarConAuthHeader("Bearer ");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void tokenExcesivamenteLargo_seDescartaPorLongitudAntesDeParsear() {
        String tokenGigante = "Bearer " + "a".repeat(5000);
        ResponseEntity<String> response = pegarConAuthHeader(tokenGigante);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void sinHeaderAuthorization_devuelve403PorFaltaDeAutenticacionNoPorExcepcion() {
        ResponseEntity<String> response = pegarConAuthHeader(null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
