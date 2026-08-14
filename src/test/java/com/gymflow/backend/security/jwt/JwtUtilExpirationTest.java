package com.gymflow.backend.security.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica que la expiración configurable (app.jwt.expiration, default 24 h)
 * se aplica de verdad al token emitido (hallazgo B-06 de la auditoría:
 * nunca se había probado la expiración real del JWT).
 *
 * Nota sobre el método de verificación: con un token vencido,
 * esTokenValido() NO devuelve false — el parseo de JJWT lanza
 * ExpiredJwtException en extraerUsername() (JwtUtil.extraerTodosClaims),
 * que es exactamente la excepción que JwtAuthFilter atrapa en :83 para
 * seguir como request anónima y dejar que el endpoint responda 401/403.
 * Por eso el segundo test asevera esa excepción: es el camino real que el
 * sistema ejecuta cuando un JWT expira, no un valor que el código jamás
 * produce.
 */
@SpringBootTest(properties = "app.jwt.expiration=1000")
class JwtUtilExpirationTest {

    @Autowired
    private JwtUtil jwtUtil;

    private UserDetails usuarioDePrueba() {
        return new User("cliente-expiracion@gymflow.test", "sin-password", List.of());
    }

    @Test
    void tokenRecienEmitidoEsValidoParaSuUsuario() {
        UserDetails usuario = usuarioDePrueba();
        String token = jwtUtil.generarToken(usuario);

        assertThat(jwtUtil.esTokenValido(token, usuario)).isTrue();
    }

    @Test
    void tokenExpiradoEsRechazadoPorElParserComoJwtException() throws InterruptedException {
        UserDetails usuario = usuarioDePrueba();
        String token = jwtUtil.generarToken(usuario);

        Thread.sleep(2000);

        assertThatThrownBy(() -> jwtUtil.extraerUsername(token))
                .isInstanceOf(ExpiredJwtException.class);
    }
}