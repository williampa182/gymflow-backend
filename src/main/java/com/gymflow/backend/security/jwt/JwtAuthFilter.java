package com.gymflow.backend.security.jwt;

import com.gymflow.backend.security.service.UserDetailsServiceImpl;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Tamaño máximo razonable para un JWT de este proyecto (claims mínimas:
 * sub, iat, exp). Cualquier cosa muy por encima de esto no es un token
 * válido nuestro y se descarta ANTES de tocar el parser de JJWT — mitiga
 * el intento de forzar trabajo de parseo/descompresión desproporcionado con
 * un header Authorization armado a mano (ver hallazgo 2.7 del threat model).
 */

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    // Un JWT nuestro real (header+payload+firma HS256, claims mínimas) nunca
    // se acerca a esto. Cortar acá evita gastar CPU del parser de JJWT en
    // strings gigantes armados a mano.
    private static final int MAX_JWT_LENGTH = 2048;

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        if (jwt.isBlank() || jwt.length() > MAX_JWT_LENGTH) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String userEmail = jwtUtil.extraerUsername(jwt);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

                if (jwtUtil.esTokenValido(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (JwtException | IllegalArgumentException e) {
            // Token malformado, expirado, con firma inválida, etc. Antes esto
            // no se atrapaba y producía una excepción sin manejar en cada
            // request con un Authorization header inválido (ver hallazgo
            // confirmado en la auditoría de seguridad). Simplemente no
            // autenticamos y dejamos que la request siga como anónima; si el
            // endpoint requiere auth, Spring Security la rechaza más abajo
            // con un 401/403 limpio.
            log.debug("JWT inválido recibido: {}", e.getMessage());
        } catch (UsernameNotFoundException e) {
            // El subject del token ya no existe (usuario borrado). Mismo
            // tratamiento: no autenticar, seguir como anónimo.
            log.debug("JWT con usuario inexistente: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}