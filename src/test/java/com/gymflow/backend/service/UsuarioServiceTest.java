package com.gymflow.backend.service;

import com.gymflow.backend.dto.CarnetResponseDTO;
import com.gymflow.backend.dto.UsuarioResponseDTO;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private CodigoCarnetGenerator codigoCarnetGenerator;

    @InjectMocks
    private UsuarioService usuarioService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder()
                .id(1L)
                .nombre("William Admin")
                .email("william@gymflow.com")
                .password("hashed")
                .rol(Rol.ADMIN)
                .activo(true)
                .build();
    }

    @Test
    void listar_sinFiltro_retornaTodos() {
        Pageable pageable = PageRequest.of(0, 20);
        when(usuarioRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(usuario)));

        Page<UsuarioResponseDTO> resultado = usuarioService.listarUsuarios(null, pageable);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().get(0).getNombre()).isEqualTo("William Admin");
    }

    @Test
    void listar_filtradoPorRol_retornaFiltrados() {
        Pageable pageable = PageRequest.of(0, 20);
        when(usuarioRepository.findByRol(Rol.ADMIN, pageable)).thenReturn(new PageImpl<>(List.of(usuario)));

        Page<UsuarioResponseDTO> resultado = usuarioService.listarUsuarios(Rol.ADMIN, pageable);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().get(0).getRol()).isEqualTo(Rol.ADMIN);
    }

    @Test
    @SuppressWarnings("null")
    void cambiarEstado_desactiva_exitoso() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.countByRolAndActivo(Rol.ADMIN, true)).thenReturn(2L);

        UsuarioResponseDTO resultado = usuarioService.cambiarEstado(1L, false);

        assertThat(resultado.isActivo()).isFalse();
        verify(usuarioRepository).save(usuario);
    }

    @Test
    @SuppressWarnings("null")
    void cambiarEstado_usuarioNoEncontrado_lanzaExcepcion() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.cambiarEstado(99L, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    @AfterEach
    void limpiarSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cambiarRol_clientePuedeSerPromovidoAEntrenador() {
        Usuario cliente = usuario(2L, "cliente@gymflow.test", Rol.CLIENTE, true);
        autenticarComo("william@gymflow.com");
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(cliente));

        UsuarioResponseDTO resultado = usuarioService.cambiarRol(2L, Rol.ENTRENADOR);

        assertThat(resultado.getRol()).isEqualTo(Rol.ENTRENADOR);
        verify(usuarioRepository).save(cliente);
        verify(usuarioRepository, never()).countByRolAndActivo(any(), anyBoolean());
    }

    @Test
    void cambiarRol_clientePuedeSerPromovidoAAdmin() {
        Usuario cliente = usuario(2L, "cliente@gymflow.test", Rol.CLIENTE, true);
        autenticarComo("william@gymflow.com");
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(cliente));

        UsuarioResponseDTO resultado = usuarioService.cambiarRol(2L, Rol.ADMIN);

        assertThat(resultado.getRol()).isEqualTo(Rol.ADMIN);
        verify(usuarioRepository).save(cliente);
    }

    @Test
    void cambiarRol_adminPuedeSerDespromovidoCuandoHayOtroAdminActivo() {
        Usuario segundoAdmin = usuario(2L, "segundo-admin@gymflow.test", Rol.ADMIN, true);
        autenticarComo("william@gymflow.com");
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(segundoAdmin));
        when(usuarioRepository.countByRolAndActivo(Rol.ADMIN, true)).thenReturn(2L);

        UsuarioResponseDTO resultado = usuarioService.cambiarRol(2L, Rol.ENTRENADOR);

        assertThat(resultado.getRol()).isEqualTo(Rol.ENTRENADOR);
        verify(usuarioRepository).countByRolAndActivo(Rol.ADMIN, true);
        verify(usuarioRepository).save(segundoAdmin);
    }

    @Test
    void cambiarRol_rechazaDespromoverAlUltimoAdminActivo() {
        autenticarComo("otro-admin@gymflow.test");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.countByRolAndActivo(Rol.ADMIN, true)).thenReturn(1L);

        assertThatThrownBy(() -> usuarioService.cambiarRol(1L, Rol.ENTRENADOR))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("último ADMIN");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cambiarRol_adminInactivoNoCuentaParaLaGuardaDeAdminsActivos() {
        Usuario segundoAdminInactivo = usuario(2L, "admin-inactivo@gymflow.test", Rol.ADMIN, false);
        autenticarComo("otro-admin@gymflow.test");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.countByRolAndActivo(Rol.ADMIN, true)).thenReturn(1L);

        assertThatThrownBy(() -> usuarioService.cambiarRol(1L, Rol.CLIENTE))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("último ADMIN");

        assertThat(segundoAdminInactivo.isActivo()).isFalse();
        verify(usuarioRepository).countByRolAndActivo(Rol.ADMIN, true);
    }

    @Test
    void cambiarRol_rechazaAutoCambioDeRolDelAdminAutenticado() {
        autenticarComo(usuario.getEmail());
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> usuarioService.cambiarRol(1L, Rol.CLIENTE))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("propio");

        verify(usuarioRepository, never()).countByRolAndActivo(any(), anyBoolean());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cambiarRol_usuarioNoEncontrado_lanzaExcepcion() {
        autenticarComo("william@gymflow.com");
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.cambiarRol(99L, Rol.CLIENTE))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    @Test
    void cambiarRol_rolNulo_lanzaExcepcion() {
        autenticarComo("william@gymflow.com");

        assertThatThrownBy(() -> usuarioService.cambiarRol(1L, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("rol");

        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void cambiarEstado_rechazaDesactivarAlUltimoAdminActivo() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.countByRolAndActivo(Rol.ADMIN, true)).thenReturn(1L);

        assertThatThrownBy(() -> usuarioService.cambiarEstado(1L, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("último ADMIN");

        assertThat(usuario.isActivo()).isTrue();
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cambiarEstado_permiteDesactivarAdminCuandoHayOtroAdminActivo() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.countByRolAndActivo(Rol.ADMIN, true)).thenReturn(2L);

        UsuarioResponseDTO resultado = usuarioService.cambiarEstado(1L, false);

        assertThat(resultado.isActivo()).isFalse();
        verify(usuarioRepository).countByRolAndActivo(Rol.ADMIN, true);
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void cambiarEstado_adminInactivoNoSeCuentaComoAdminActivo() {
        usuario.setActivo(false);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        UsuarioResponseDTO resultado = usuarioService.cambiarEstado(1L, true);

        assertThat(resultado.isActivo()).isTrue();
        verify(usuarioRepository, never()).countByRolAndActivo(any(), anyBoolean());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void obtenerCarnet_adminVeCarnetDelUsuario() {
        Usuario conCarnet = usuario(1L, "william@gymflow.com", Rol.ADMIN, true);
        conCarnet.setCodigoCarnet("ABC123");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(conCarnet));

        CarnetResponseDTO resultado = usuarioService.obtenerCarnet(1L);

        assertThat(resultado.getCodigoCarnet()).isEqualTo("ABC123");
        assertThat(resultado.getNombre()).isEqualTo("Usuario 1");
    }

    @Test
    void obtenerCarnet_usuarioNoEncontrado_lanzaExcepcion() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.obtenerCarnet(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    @Test
    void obtenerCarnet_usuarioSinCodigo_lanza404() {
        usuario.setCodigoCarnet(null);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> usuarioService.obtenerCarnet(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Código de carnet no encontrado");
    }

    @Test
    void rotarCarnet_generaNuevoCodigoYLoGuarda() {
        Usuario conCarnet = usuario(1L, "william@gymflow.com", Rol.ADMIN, true);
        conCarnet.setCodigoCarnet("ABC123");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(conCarnet));
        when(codigoCarnetGenerator.generarUnico(any())).thenReturn("XYZ789");

        CarnetResponseDTO resultado = usuarioService.rotarCarnet(1L);

        assertThat(resultado.getCodigoCarnet()).isEqualTo("XYZ789");
        assertThat(resultado.getNombre()).isNull();
        assertThat(conCarnet.getCodigoCarnet()).isEqualTo("XYZ789");
        verify(codigoCarnetGenerator).generarUnico(any());
        verify(usuarioRepository).save(conCarnet);
    }

    @Test
    void rotarCarnet_generadorAgotaReintentos_noCambiaElCodigo() {
        Usuario conCarnet = usuario(1L, "william@gymflow.com", Rol.ADMIN, true);
        conCarnet.setCodigoCarnet("ABC123");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(conCarnet));
        when(codigoCarnetGenerator.generarUnico(any()))
                .thenThrow(new RuntimeException("No se pudo generar un codigo de carnet unico"));

        assertThatThrownBy(() -> usuarioService.rotarCarnet(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("No se pudo generar un codigo de carnet unico");

        assertThat(conCarnet.getCodigoCarnet()).isEqualTo("ABC123");
        verify(usuarioRepository, never()).save(any());
    }

    private Usuario usuario(Long id, String email, Rol rol, boolean activo) {
        return Usuario.builder()
                .id(id)
                .nombre("Usuario " + id)
                .email(email)
                .password("hashed")
                .rol(rol)
                .activo(activo)
                .build();
    }

    private void autenticarComo(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
}
