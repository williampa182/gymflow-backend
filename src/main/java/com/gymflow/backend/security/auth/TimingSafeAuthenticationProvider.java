package com.gymflow.backend.security.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Fix B (user-enumeration-3.1.md): iguala el timing del login entre usuarios
 * existentes e inexistentes.
 *
 * Problema: DaoAuthenticationProvider de Spring Security solo ejecuta BCrypt
 * (lento, ~100-300ms a cost 12) cuando el usuario existe. Si el email no
 * existe, UserDetailsServiceImpl lanza UsernameNotFoundException inmediatamente
 * y la respuesta vuelve en ~5ms. Ese delta es medible y permite enumerar
 * cuentas válidas con estadística básica.
 *
 * Solución: este wrapper envuelve al AuthenticationManager default. Si el
 * delegado lanza UsernameNotFoundException, ejecutamos un BCrypt.matches()
 * contra un hash precomputado antes de re-lanzar como BadCredentialsException.
 * El hash no matchea ningún password real — su único propósito es consumir
 * el mismo tiempo de CPU que consumiría un login legítimo, eliminando el
 * delta medible.
 *
 * Por qué un AuthenticationManager wrapper y no un UserDetailsService custom:
 * la alternativa de devolver un UserDetails "dummy" desde loadUserByUsername()
 * complica el chequeo de isEnabled() / isAccountNonLocked() (que en
 * Usuario.java dependen de `activo`) — el dummy tendría que pasar esos
 * checks para que BCrypt se ejecute, y eso interfere con la lógica de
 * usuarios desactivados. El wrapper es más limio: intercepta la excepción
 * después de que el flujo normal ya falló, y solo agrega el costo de CPU
 * del BCrypt dummy.
 *
 * Esta clase NO es un @Component: se instancia y se cablea manualmente en
 * SecurityConfig.authenticationManager() para envolver al AuthenticationManager
 * default que Spring construye a partir del UserDetailsService + PasswordEncoder
 * configurados. Si fuera @Component con @Primary, crearía una dependencia
 * circular (necesitaría inyectarse a sí mismo como delegate).
 */
@RequiredArgsConstructor
@Slf4j
public class TimingSafeAuthenticationProvider implements AuthenticationManager {

    private final AuthenticationProvider delegate;
    private final PasswordEncoder passwordEncoder;

    // Hash BCrypt precomputado de un password cualquiera con cost factor 12
    // (el mismo que usa SecurityConfig.BCRYPT_STRENGTH). NO corresponde a
    // ningún password real del sistema. Solo se usa para consumir CPU y
    // igualar el timing cuando el usuario no existe.
    private static final String DUMMY_HASH =
        "$2a$12$N9qo8uLOickgx2ZMRZoMy.Mrq8VcVjKZB4T9qLCcFQv8Uu2mBbWCi";

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        try {
            return delegate.authenticate(authentication);
        } catch (UsernameNotFoundException e) {
            // El usuario no existe. Antes de re-lanzar, ejecutamos un
            // BCrypt dummy para que el tiempo total sea ~igual al de un
            // login con password incorrecta de un usuario existente.
            Object credentials = authentication.getCredentials();
            String rawPassword = credentials != null ? credentials.toString() : "";
            passwordEncoder.matches(rawPassword, DUMMY_HASH);

            // Re-lanzamos como BadCredentialsException (mensaje genérico) —
            // igual que el caso de password incorrecta. El GlobalExceptionHandler
            // ya mapea esto a "Email o contraseña incorrectos".
            throw new BadCredentialsException("Email o contraseña incorrectos");
        }
    }
}
