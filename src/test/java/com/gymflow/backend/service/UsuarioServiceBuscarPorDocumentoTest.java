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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceBuscarPorDocumentoTest {

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

    @Test
    void encuentraYNormalizaAMayusculas() {
        Usuario usuario = Usuario.builder()
                .id(3L).nombre("Socio").email("s@test.com").rol(Rol.CLIENTE)
                .tipoDocumento("CC").numeroDocumento("123").build();
        when(usuarioRepository.findByTipoDocumentoAndNumeroDocumento("CC", "123"))
                .thenReturn(Optional.of(usuario));

        UsuarioResponseDTO respuesta = usuarioService.buscarPorDocumento("cc", " 123 ");

        assertThat(respuesta.getId()).isEqualTo(3L);
        assertThat(respuesta.getNumeroDocumento()).isEqualTo("123");
    }

    @Test
    void inexistente_lanzaNoEncontrado() {
        when(usuarioRepository.findByTipoDocumentoAndNumeroDocumento(any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.buscarPorDocumento("CC", "999"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No se encontró");
    }
}
