package com.gymflow.backend.service;

import com.gymflow.backend.config.SecurityConfig;
import com.gymflow.backend.dto.request.LoginRequest;
import com.gymflow.backend.dto.request.RegisterRequest;
import com.gymflow.backend.dto.response.AuthResponse;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.repository.UsuarioRepository;
import com.gymflow.backend.security.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    // Extrae el cost factor embebido en un hash BCrypt ($2a$12$... -> "12").
    // Ver hallazgo del 18/07 en collab/aplicado/: un cost desalineado entre
    // el hash real de un usuario y TimingSafeAuthenticationProvider.DUMMY_HASH
    // reabre el canal de timing que Fix B (user-enumeration-3.1.md) cerró.
    private static final Pattern BCRYPT_COST_PATTERN = Pattern.compile("^\\$2[aby]\\$(\\d+)\\$");

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    // Fix A (user-enumeration-3.1.md). Decisión de default y razonamiento:
    // ver collab/aplicado/2026-07-16-decision-reveal-email-false.md
    // (William, 2026-07-16, con contexto y trazabilidad en el repo).
    // Resumen: default false por consistencia con Fix B (TimingSafeAuth),
    // que blindó login contra enumeración por timing — registro no puede
    // abrir el mismo vector por acá.
    @Value("${app.security.reveal-email-exists-on-register:false}")
    private boolean revealEmailExists;

    // Fix security-deep-dive §4 (GLM-5.2): Opción C de la propuesta.
    // Controla si AuthResponse incluye el token JWT en el JSON de respuesta de login/register.
    // Default true mantiene el comportamiento actual intacto.
    @Value("${app.jwt.include-token-in-response:true}")
    private boolean includeTokenInResponse = true;

    @SuppressWarnings("null")
    public AuthResponse registrar(RegisterRequest request) {
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            if (revealEmailExists) {
                throw new RuntimeException("Ya existe un usuario con ese email");
            } else {
                // Modo genérico: no confirma que el email existe, pero da
                // una pista útil que orienta sin enumerar.
                throw new RuntimeException(
                    "No se pudo completar el registro. Si ya tienes una cuenta, intenta iniciar sesión.");
            }
        }

        // Rol SIEMPRE forzado a CLIENTE en el registro público: nunca tomar
        // el rol del input del cliente acá (ver comentario en RegisterRequest).
        Usuario usuario = Usuario.builder()
                .nombre(request.getNombre())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .rol(Rol.CLIENTE)
                .build();

        usuarioRepository.save(usuario);
        String token = jwtUtil.generarToken(usuario);
        return buildResponse(token, usuario);
    }

    public AuthResponse login(LoginRequest request) {
        // Fix C (user-enumeration-3.1.md): antes había un findByEmail()
        // .orElseThrow() acá abajo que era dead code (el authManager ya
        // validó que el usuario existe y la contraseña es correcta arriba),
        // pero si por algún refactor el flujo cambiaba, ese camino latente
        // hubiera filtrado "Usuario no encontrado" al cliente via el
        // GlobalExceptionHandler. Ahora usamos el Authentication object que
        // Spring ya devolvió para obtener el email, y si findByEmail falla
        // es una inconsistencia real (no un 404 de negocio) que se trata
        // como IllegalStateException, no como RuntimeException genérica.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        String email = authentication.getName();
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException(
                    "Usuario autenticado no encontrado en BD — inconsistencia de estado"));

        rehashIfNeeded(usuario, request.getPassword());

        String token = jwtUtil.generarToken(usuario);
        return buildResponse(token, usuario);
    }

    // Rehash-on-login: si el hash almacenado quedó en un cost factor menor
    // al configurado en SecurityConfig.BCRYPT_STRENGTH (ej. usuarios sembrados
    // antes de subir el cost, o si se vuelve a subir en el futuro), lo
    // regeneramos acá usando la contraseña en texto plano que ya tenemos
    // disponible porque la autenticación arriba ya la validó. Silencioso
    // para el usuario (no cambia el flujo de login), pero deja rastro en log
    // para trazabilidad. Si el hash no matchea el patrón esperado (formato
    // corrupto o no-BCrypt), no tocamos nada — mejor un hash desactualizado
    // que uno corrupto.
    private void rehashIfNeeded(Usuario usuario, String rawPassword) {
        Matcher matcher = BCRYPT_COST_PATTERN.matcher(usuario.getPassword());
        if (!matcher.find()) {
            return;
        }
        int costoActual = Integer.parseInt(matcher.group(1));
        if (costoActual < SecurityConfig.BCRYPT_STRENGTH) {
            log.info("Rehasheando password de usuario id={} (cost {} -> {})",
                    usuario.getId(), costoActual, SecurityConfig.BCRYPT_STRENGTH);
            usuario.setPassword(passwordEncoder.encode(rawPassword));
            usuarioRepository.save(usuario);
        }
    }

    private AuthResponse buildResponse(String token, Usuario usuario) {
        return AuthResponse.builder()
                .token(includeTokenInResponse ? token : null)
                .tipo(includeTokenInResponse ? "Bearer" : null)
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .rol(usuario.getRol())
                .build();
    }
}