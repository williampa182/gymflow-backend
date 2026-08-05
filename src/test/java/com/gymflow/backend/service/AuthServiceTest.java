package com.gymflow.backend.service;

import com.gymflow.backend.dto.request.LoginRequest;
import com.gymflow.backend.dto.request.RegisterRequest;
import com.gymflow.backend.dto.response.AuthResponse;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.repository.UsuarioRepository;
import com.gymflow.backend.security.jwt.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "cliente@gymflow.com";
    private static final String RAW_PASSWORD = "password-secreta";
    private static final String CURRENT_COST_HASH = "$2a$12$hash-con-cost-vigente";
    private static final String OUTDATED_COST_HASH = "$2a$10$hash-con-cost-viejo";
    private static final String UPGRADED_HASH = "$2a$12$hash-rehasheado";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private CodigoCarnetGenerator codigoCarnetGenerator;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                usuarioRepository, passwordEncoder, jwtUtil, authenticationManager, codigoCarnetGenerator);
    }

    private void stubGeneracionDeCarnet() {
        when(codigoCarnetGenerator.generarUnico(any())).thenReturn("ABC123");
    }

    @Test
    void registrar_emailExistenteYRevealHabilitado_lanzaMensajeEspecifico() {
        RegisterRequest request = registerRequest();
        ReflectionTestUtils.setField(authService, "revealEmailExists", true);
        when(usuarioRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> authService.registrar(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Ya existe un usuario con ese email");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void registrar_emailExistenteYRevealDeshabilitado_lanzaMensajeGenerico() {
        RegisterRequest request = registerRequest();
        ReflectionTestUtils.setField(authService, "revealEmailExists", false);
        when(usuarioRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> authService.registrar(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("No se pudo completar el registro. Si ya tienes una cuenta, intenta iniciar sesi\u00f3n.");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void registrar_emailDisponible_guardaClienteYRetornaTokenGenerado() {
        RegisterRequest request = registerRequest();
        when(usuarioRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(usuarioRepository.countByRolAndActivo(Rol.ADMIN, true)).thenReturn(1L);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(CURRENT_COST_HASH);
        when(jwtUtil.generarToken(any(Usuario.class))).thenReturn("jwt-generado");
        stubGeneracionDeCarnet();

        AuthResponse response = authService.registrar(request);

        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioCaptor.capture());
        Usuario usuarioGuardado = usuarioCaptor.getValue();
        assertThat(usuarioGuardado.getRol()).isEqualTo(Rol.CLIENTE);
        assertThat(usuarioGuardado.getNombre()).isEqualTo(request.getNombre());
        assertThat(usuarioGuardado.getEmail()).isEqualTo(EMAIL);
        assertThat(usuarioGuardado.getPassword()).isEqualTo(CURRENT_COST_HASH);
        assertThat(usuarioGuardado.getCodigoCarnet()).isEqualTo("ABC123");
        verify(jwtUtil).generarToken(usuarioGuardado);
        assertThat(response)
                .extracting(AuthResponse::getToken, AuthResponse::getTipo, AuthResponse::getEmail, AuthResponse::getRol)
                .containsExactly("jwt-generado", "Bearer", EMAIL, Rol.CLIENTE);
    }

    @Test
    void registrar_generaCodigoDeCarnetUnicoYLoGuarda() {
        RegisterRequest request = registerRequest();
        when(usuarioRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(usuarioRepository.countByRolAndActivo(Rol.ADMIN, true)).thenReturn(1L);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(CURRENT_COST_HASH);
        when(jwtUtil.generarToken(any(Usuario.class))).thenReturn("jwt-generado");
        stubGeneracionDeCarnet();

        authService.registrar(request);

        // El predicado que el service le pasa al generador consulta el índice
        // único (existsByCodigoCarnet), no queda muerto.
        ArgumentCaptor<Predicate<String>> predicadoCaptor = ArgumentCaptor.forClass(Predicate.class);
        verify(codigoCarnetGenerator).generarUnico(predicadoCaptor.capture());
        when(usuarioRepository.existsByCodigoCarnet("ABC123")).thenReturn(true);
        assertThat(predicadoCaptor.getValue().test("ABC123")).isTrue();
    }

    @Test
    void registrar_generadorAgotaReintentos_noGuardaUsuarioYLanza() {
        RegisterRequest request = registerRequest();
        when(usuarioRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(usuarioRepository.countByRolAndActivo(Rol.ADMIN, true)).thenReturn(1L);
        when(codigoCarnetGenerator.generarUnico(any()))
                .thenThrow(new RuntimeException("No se pudo generar un codigo de carnet unico"));

        assertThatThrownBy(() -> authService.registrar(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("No se pudo generar un codigo de carnet unico");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void registrar_conRolEntrenadorEnWhitelist_guardaEntrenador() {
        RegisterRequest request = registerRequest();
        request.setRol("ENTRENADOR");
        when(usuarioRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(usuarioRepository.countByRolAndActivo(Rol.ADMIN, true)).thenReturn(1L);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(CURRENT_COST_HASH);
        when(jwtUtil.generarToken(any(Usuario.class))).thenReturn("jwt-generado");

        AuthResponse response = authService.registrar(request);

        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioCaptor.capture());
        assertThat(usuarioCaptor.getValue().getRol()).isEqualTo(Rol.ENTRENADOR);
        assertThat(response.getRol()).isEqualTo(Rol.ENTRENADOR);
    }

    @Test
    void registrar_rolAdminEnElBody_nuncaEscalaYQuedaCliente() {
        RegisterRequest request = registerRequest();
        request.setRol("ADMIN");
        when(usuarioRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(usuarioRepository.countByRolAndActivo(Rol.ADMIN, true)).thenReturn(1L);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(CURRENT_COST_HASH);
        when(jwtUtil.generarToken(any(Usuario.class))).thenReturn("jwt-generado");

        AuthResponse response = authService.registrar(request);

        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioCaptor.capture());
        assertThat(usuarioCaptor.getValue().getRol()).isEqualTo(Rol.CLIENTE);
        assertThat(response.getRol()).isEqualTo(Rol.CLIENTE);
    }

    @Test
    void registrar_rolDesconocido_oMinusculas_seDegradaACliente() {
        RegisterRequest request = registerRequest();
        request.setRol("súper-VIP");
        when(usuarioRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(usuarioRepository.countByRolAndActivo(Rol.ADMIN, true)).thenReturn(1L);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(CURRENT_COST_HASH);
        when(jwtUtil.generarToken(any(Usuario.class))).thenReturn("jwt-generado");

        authService.registrar(request);

        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioCaptor.capture());
        assertThat(usuarioCaptor.getValue().getRol()).isEqualTo(Rol.CLIENTE);
    }

    @Test
    void registrar_sinAdminsActivos_primerRegistroNaceAdmin_bootstrap() {
        RegisterRequest request = registerRequest();
        request.setRol("CLIENTE");
        when(usuarioRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(usuarioRepository.countByRolAndActivo(Rol.ADMIN, true)).thenReturn(0L);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(CURRENT_COST_HASH);
        when(jwtUtil.generarToken(any(Usuario.class))).thenReturn("jwt-generado");

        AuthResponse response = authService.registrar(request);

        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioCaptor.capture());
        assertThat(usuarioCaptor.getValue().getRol())
                .as("sin ningún ADMIN activo, el primer registro nace ADMIN (bootstrap)")
                .isEqualTo(Rol.ADMIN);
        assertThat(response.getRol()).isEqualTo(Rol.ADMIN);
    }

    @Test
    void elPrimerRegistroSeraAdmin_reflejaLaFaltaDeAdminsActivos() {
        when(usuarioRepository.countByRolAndActivo(Rol.ADMIN, true)).thenReturn(0L);
        assertThat(authService.elPrimerRegistroSeraAdmin()).isTrue();

        when(usuarioRepository.countByRolAndActivo(Rol.ADMIN, true)).thenReturn(2L);
        assertThat(authService.elPrimerRegistroSeraAdmin()).isFalse();
    }

    @Test
    void login_autenticacionYUsuarioValidos_retornaRespuestaPoblada() {
        LoginRequest request = loginRequest();
        Authentication authentication = mock(Authentication.class);
        Usuario usuario = usuario(CURRENT_COST_HASH);
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authentication);
        when(authentication.getName()).thenReturn(EMAIL);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(jwtUtil.generarToken(usuario)).thenReturn("jwt-generado");

        AuthResponse response = authService.login(request);

        ArgumentCaptor<Authentication> authenticationCaptor = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(authenticationCaptor.capture());
        UsernamePasswordAuthenticationToken authRequest =
                (UsernamePasswordAuthenticationToken) authenticationCaptor.getValue();
        assertThat(authRequest.getPrincipal()).isEqualTo(EMAIL);
        assertThat(authRequest.getCredentials()).isEqualTo(RAW_PASSWORD);
        assertThat(response)
                .extracting(AuthResponse::getToken, AuthResponse::getTipo, AuthResponse::getId,
                        AuthResponse::getNombre, AuthResponse::getEmail, AuthResponse::getRol)
                .containsExactly("jwt-generado", "Bearer", 1L, "Cliente GymFlow", EMAIL, Rol.CLIENTE);
    }

    @Test
    void login_autenticadoSinUsuarioEnBase_lanzaIllegalStateException() {
        LoginRequest request = loginRequest();
        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authentication);
        when(authentication.getName()).thenReturn(EMAIL);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Usuario autenticado no encontrado en BD");
    }

    @Test
    void login_hashConCostVigente_noRehashea() {
        LoginRequest request = loginRequest();
        Authentication authentication = authenticatedAs(EMAIL);
        Usuario usuario = usuario(CURRENT_COST_HASH);
        prepareSuccessfulLogin(authentication, usuario);

        authService.login(request);

        verify(passwordEncoder, never()).encode(anyString());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void login_hashConCostMenor_rehasheaYConservaLaRespuesta() {
        LoginRequest request = loginRequest();
        Authentication authentication = authenticatedAs(EMAIL);
        Usuario usuario = usuario(OUTDATED_COST_HASH);
        prepareSuccessfulLogin(authentication, usuario);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(UPGRADED_HASH);

        AuthResponse response = authService.login(request);

        verify(passwordEncoder).encode(RAW_PASSWORD);
        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioCaptor.capture());
        assertThat(usuarioCaptor.getValue()).isSameAs(usuario);
        assertThat(usuarioCaptor.getValue().getPassword()).isEqualTo(UPGRADED_HASH);
        assertThat(response)
                .extracting(AuthResponse::getToken, AuthResponse::getTipo, AuthResponse::getEmail, AuthResponse::getRol)
                .containsExactly("jwt-generado", "Bearer", EMAIL, Rol.CLIENTE);
    }

    @Test
    void login_hashConFormatoNoReconocido_noRehashea() {
        LoginRequest request = loginRequest();
        Authentication authentication = authenticatedAs(EMAIL);
        Usuario usuario = usuario("hash-corrupto");
        prepareSuccessfulLogin(authentication, usuario);

        authService.login(request);

        verify(passwordEncoder, never()).encode(anyString());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void login_includeTokenInResponseDeshabilitado_retornaRespuestaSinToken() {
        LoginRequest request = loginRequest();
        Authentication authentication = authenticatedAs(EMAIL);
        Usuario usuario = usuario(CURRENT_COST_HASH);
        prepareSuccessfulLogin(authentication, usuario);
        ReflectionTestUtils.setField(authService, "includeTokenInResponse", false);

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isNull();
        assertThat(response.getTipo()).isNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo(EMAIL);
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setNombre("Cliente GymFlow");
        request.setEmail(EMAIL);
        request.setPassword(RAW_PASSWORD);
        return request;
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setEmail(EMAIL);
        request.setPassword(RAW_PASSWORD);
        return request;
    }

    private Usuario usuario(String password) {
        return Usuario.builder()
                .id(1L)
                .nombre("Cliente GymFlow")
                .email(EMAIL)
                .password(password)
                .rol(Rol.CLIENTE)
                .build();
    }

    private Authentication authenticatedAs(String email) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(email);
        return authentication;
    }

    private void prepareSuccessfulLogin(Authentication authentication, Usuario usuario) {
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authentication);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(jwtUtil.generarToken(usuario)).thenReturn("jwt-generado");
    }
}
