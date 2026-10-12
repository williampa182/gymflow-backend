package com.gymflow.backend.repository;

import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.Rol;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Búsqueda por texto con documento (hallazgo E2E 2026-10-12: la
 * recepcionista conoce el CC pero no el email). Contra Postgres real porque
 * el ILIKE con ESCAPE solo se prueba de verdad en el motor.
 */
@SpringBootTest
@Transactional
class UsuarioRepositoryBuscarTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Usuario guardar(String nombre, String email, String numeroDocumento) {
        return usuarioRepository.save(Usuario.builder()
                .nombre(nombre)
                .email(email)
                .password("hashed")
                .rol(Rol.CLIENTE)
                .tipoDocumento("CC")
                .numeroDocumento(numeroDocumento)
                .build());
    }

    @Test
    void buscarPorTexto_encuentraPorFragmentoDeDocumento() {
        Usuario socio = guardar("Socio Doc", "socio-doc@test.local", "987654321");

        Page<Usuario> resultado = usuarioRepository.buscarPorTexto(
                null, "76543", PageRequest.of(0, 20));

        assertThat(resultado.getContent()).extracting(Usuario::getId).contains(socio.getId());
    }

    @Test
    void buscarPorTexto_sigueEncontrandoPorNombreYEmail() {
        Usuario socio = guardar("Socio Nombre", "socio-nombre@test.local", "111222333");

        assertThat(usuarioRepository.buscarPorTexto(null, "nombre", PageRequest.of(0, 20))
                .getContent()).extracting(Usuario::getId).contains(socio.getId());
        assertThat(usuarioRepository.buscarPorTexto(null, "socio-nombre@test", PageRequest.of(0, 20))
                .getContent()).extracting(Usuario::getId).contains(socio.getId());
    }

    @Test
    void buscarPorTexto_sinCoincidencia_vacio() {
        guardar("Socio X", "socio-x@test.local", "111222333");

        assertThat(usuarioRepository.buscarPorTexto(null, "zzz-no-existe", PageRequest.of(0, 20))
                .getContent()).isEmpty();
    }
}
