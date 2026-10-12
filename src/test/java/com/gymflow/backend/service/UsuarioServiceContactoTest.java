package com.gymflow.backend.service;

import com.gymflow.backend.dto.UsuarioResponseDTO;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.repository.AsignacionEntrenadorRepository;
import com.gymflow.backend.repository.AsignacionRutinaRepository;
import com.gymflow.backend.repository.AsistenciaRepository;
import com.gymflow.backend.repository.RutinaRepository;
import com.gymflow.backend.repository.SuscripcionRepository;
import com.gymflow.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceContactoTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private CodigoCarnetGenerator codigoCarnetGenerator;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private jakarta.validation.Validator validator;

    @Mock
    private AsistenciaRepository asistenciaRepository;

    @Mock
    private AsignacionRutinaRepository asignacionRutinaRepository;

    @Mock
    private RutinaRepository rutinaRepository;

    @Mock
    private AsignacionEntrenadorRepository asignacionEntrenadorRepository;

    @Mock
    private SuscripcionRepository suscripcionRepository;

    @InjectMocks
    private UsuarioService usuarioService;

    private Usuario socio() {
        return Usuario.builder()
                .id(5L)
                .nombre("Socio")
                .email("socio@gymflow.com")
                .rol(Rol.CLIENTE)
                .telefono("3001112222")
                .build();
    }

    @Test
    void actualizaTelefonoYEmail_guardaNormalizado() {
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(socio()));
        when(usuarioRepository.existsByEmail("nuevo@gymflow.com")).thenReturn(false);

        UsuarioResponseDTO respuesta = usuarioService.actualizarContacto(5L, "Nuevo@GymFlow.com", " 3009998877 ");

        assertThat(respuesta.getEmail()).isEqualTo("nuevo@gymflow.com");
        assertThat(respuesta.getTelefono()).isEqualTo("3009998877");
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("nuevo@gymflow.com");
        assertThat(captor.getValue().getTelefono()).isEqualTo("3009998877");
    }

    @Test
    void camposVacios_limpianSinConsultarDuplicados() {
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(socio()));

        UsuarioResponseDTO respuesta = usuarioService.actualizarContacto(5L, "   ", "");

        assertThat(respuesta.getEmail()).isNull();
        assertThat(respuesta.getTelefono()).isNull();
        verify(usuarioRepository, never()).existsByEmail(any());
    }

    @Test
    void emailDuplicadoDeOtro_lanzaConflicto() {
        Usuario otro = socio();
        otro.setId(6L);
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(socio()));
        when(usuarioRepository.existsByEmail("otro@gymflow.com")).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.actualizarContacto(5L, "OTRO@gymflow.com", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Ya existe");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void mismoEmailPropioConDistintaCaja_noChoca() {
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(socio()));

        UsuarioResponseDTO respuesta = usuarioService.actualizarContacto(5L, "SOCIO@gymflow.com", null);

        assertThat(respuesta.getEmail()).isEqualTo("socio@gymflow.com");
        verify(usuarioRepository, never()).existsByEmail(any());
        verify(usuarioRepository).save(any());
    }

    @Test
    void inexistente_lanzaNoEncontrado() {
        when(usuarioRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.actualizarContacto(9L, "a@b.com", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no encontrado");
    }
}
