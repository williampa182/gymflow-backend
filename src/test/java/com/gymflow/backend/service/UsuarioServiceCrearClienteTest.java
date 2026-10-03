package com.gymflow.backend.service;

import com.gymflow.backend.dto.UsuarioResponseDTO;
import com.gymflow.backend.dto.request.CrearClienteRequest;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceCrearClienteTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private CodigoCarnetGenerator codigoCarnetGenerator;

    @Mock
    private PasswordEncoder passwordEncoder;

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

    private CrearClienteRequest pedido(String tipo, String numero) {
        CrearClienteRequest request = new CrearClienteRequest();
        request.setNombre("Socio Nuevo");
        request.setEmail("socio@gymflow.com");
        request.setTipoDocumento(tipo);
        request.setNumeroDocumento(numero);
        request.setTelefono("3001234567");
        return request;
    }

    @Test
    void creaClienteConRolForzadoYCarnet() {
        when(usuarioRepository.existsByEmail("socio@gymflow.com")).thenReturn(false);
        when(usuarioRepository.existsByTipoDocumentoAndNumeroDocumento("CC", "123456"))
                .thenReturn(false);
        when(codigoCarnetGenerator.generarUnico(any())).thenReturn("ABC123");
        when(passwordEncoder.encode(any())).thenReturn("hash");

        UsuarioResponseDTO respuesta = usuarioService.crearCliente(pedido("CC", "123456"));

        assertThat(respuesta.getRol()).isEqualTo(Rol.CLIENTE);
        assertThat(respuesta.getTipoDocumento()).isEqualTo("CC");
        assertThat(respuesta.getNumeroDocumento()).isEqualTo("123456");
        assertThat(respuesta.getTelefono()).isEqualTo("3001234567");
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertThat(captor.getValue().getRol()).isEqualTo(Rol.CLIENTE);
        assertThat(captor.getValue().getCodigoCarnet()).isEqualTo("ABC123");
        assertThat(captor.getValue().getPassword()).isEqualTo("hash");
        assertThat(captor.getValue().isActivo()).isTrue();
    }

    @Test
    void normalizaTipoYNumeroAMayusculas() {
        when(usuarioRepository.existsByEmail(any())).thenReturn(false);
        when(usuarioRepository.existsByTipoDocumentoAndNumeroDocumento("PASAPORTE", "AB123"))
                .thenReturn(false);
        when(codigoCarnetGenerator.generarUnico(any())).thenReturn("XYZ789");
        when(passwordEncoder.encode(any())).thenReturn("hash");

        UsuarioResponseDTO respuesta = usuarioService.crearCliente(pedido("pasaporte", " ab123 "));

        assertThat(respuesta.getTipoDocumento()).isEqualTo("PASAPORTE");
        assertThat(respuesta.getNumeroDocumento()).isEqualTo("AB123");
    }

    @Test
    void emailDuplicado_lanzaConflicto() {
        when(usuarioRepository.existsByEmail("socio@gymflow.com")).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.crearCliente(pedido("CC", "123456")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Ya existe");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void documentoDuplicado_lanzaConflicto() {
        when(usuarioRepository.existsByEmail(any())).thenReturn(false);
        when(usuarioRepository.existsByTipoDocumentoAndNumeroDocumento("CC", "123456"))
                .thenReturn(true);

        assertThatThrownBy(() -> usuarioService.crearCliente(pedido("CC", "123456")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Ya existe");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void sinEmail_creaClienteConEmailNulo() {
        CrearClienteRequest request = pedido("CC", "123456");
        request.setEmail(null);
        when(usuarioRepository.existsByTipoDocumentoAndNumeroDocumento("CC", "123456"))
                .thenReturn(false);
        when(codigoCarnetGenerator.generarUnico(any())).thenReturn("ABC123");
        when(passwordEncoder.encode(any())).thenReturn("hash");

        UsuarioResponseDTO respuesta = usuarioService.crearCliente(request);

        assertThat(respuesta.getEmail()).isNull();
        verify(usuarioRepository, never()).existsByEmail(any());
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isNull();
    }

    @Test
    void emailVacio_seNormalizaANulo() {
        CrearClienteRequest request = pedido("CC", "123456");
        request.setEmail("   ");
        when(usuarioRepository.existsByTipoDocumentoAndNumeroDocumento("CC", "123456"))
                .thenReturn(false);
        when(codigoCarnetGenerator.generarUnico(any())).thenReturn("ABC123");
        when(passwordEncoder.encode(any())).thenReturn("hash");

        UsuarioResponseDTO respuesta = usuarioService.crearCliente(request);

        assertThat(respuesta.getEmail()).isNull();
        verify(usuarioRepository, never()).existsByEmail(any());
    }
}
