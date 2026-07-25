package com.gymflow.backend.config;

import com.gymflow.backend.security.auth.TimingSafeAuthenticationProvider;
import com.gymflow.backend.security.filter.LoginRateLimitFilter;
import com.gymflow.backend.security.jwt.JwtAuthFilter;
import com.gymflow.backend.security.service.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // Cost factor de BCrypt fijado explícitamente (hallazgo 4.1 del
    // THREAT_MODEL.md). Antes se usaba el default implícito de la librería,
    // que no era una decisión visible del proyecto. 12 es el mínimo
    // recomendado hoy. Subir esto solo afecta passwords nuevos por defecto —
    // los hashes existentes con menor costo siguen validando porque el costo
    // va embebido en el propio hash BCrypt. OJO: eso reabre el mismo canal de
    // timing que cerró Fix B (user-enumeration-3.1.md) si un usuario viejo
    // queda en un costo distinto al de TimingSafeAuthenticationProvider.DUMMY_HASH
    // (nos pasó: hallazgo del 18/07, ver collab/aplicado/). Por eso
    // AuthService.login() rehashea automáticamente en el primer login exitoso
    // si detecta un costo desactualizado — public para que AuthService pueda
    // comparar contra este valor sin duplicarlo.
    public static final int BCRYPT_STRENGTH = 12;

    private final JwtAuthFilter jwtAuthFilter;
    private final LoginRateLimitFilter loginRateLimitFilter;
    private final UserDetailsServiceImpl userDetailsService;
    private final PasswordEncoder passwordEncoder;

    // Origenes permitidos para CORS. En local por defecto solo localhost:3000;
    // en Railway se define ALLOWED_ORIGINS con la(s) URL(s) real(es) del
    // frontend, separadas por coma (ej: https://gymflow-frontend.up.railway.app).
    @Value("${ALLOWED_ORIGINS:http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                // §2 (security-deep-dive-additional-findings.md): los endpoints
                // de Swagger/OpenAPI se controlan via springdoc.api-docs.enabled
                // y springdoc.swagger-ui.enabled en application.yaml (default
                // false). Cuando están deshabilitados, springdoc NO registra
                // los handlers — estos permitAll() no matchean nada en
                // producción, son inofensivos. En dev (SWAGGER_ENABLED=true),
                // springdoc registra los handlers y estos permitAll() los
                // dejan accesibles sin auth, que es lo querido para debugging.
                // No condicionamos acá con @ConditionalOnProperty para no
                // agregar complejidad: la defensa real está en el yaml.
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**"
                ).permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                // Endpoint de diagnóstico para verificar el comportamiento real
                // de X-Forwarded-For/RemoteIpValve (fix hallazgo 2.2). Igual que
                // Swagger arriba: el propio bean está gateado por
                // @ConditionalOnProperty (app.debug-headers.enabled, default
                // false) — si el flag está apagado el controller ni se registra,
                // así que este permitAll() es inofensivo en producción. Sin auth
                // porque el uso es verificación manual puntual, no un endpoint
                // que deba quedar activo de forma permanente.
                .requestMatchers("/api/v1/debug/**").permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        // Fix B (user-enumeration-3.1.md): construimos explícitamente el
        // DaoAuthenticationProvider con nuestro UserDetailsService y
        // PasswordEncoder, lo envolvemos en TimingSafeAuthenticationProvider
        // que ejecuta un BCrypt dummy cuando el usuario no existe (eliminando
        // el delta de timing que permitía enumeración), y exponemos el wrapper
        // como AuthenticationManager.
        //
        // Construimos manualmente en vez de usar
        // AuthenticationConfiguration.getAuthenticationManager() para tener
        // control total sobre qué provider se usa y poder envolverlo. La
        // config automática de Spring haría difícil interceptar el provider
        // default sin ambigüedad.
        DaoAuthenticationProvider daoProvider = new DaoAuthenticationProvider(userDetailsService);
        daoProvider.setPasswordEncoder(passwordEncoder);

        return new TimingSafeAuthenticationProvider(daoProvider, passwordEncoder);
    }

    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}