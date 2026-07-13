package com.gymflow.backend.service;

import com.gymflow.backend.dto.UsuarioResponseDTO;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.repository.UsuarioRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

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
}
