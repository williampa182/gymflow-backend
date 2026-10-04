package com.gymflow.backend.service;

import com.gymflow.backend.dto.ResultadoImportacionDTO;
import com.gymflow.backend.repository.AsignacionEntrenadorRepository;
import com.gymflow.backend.repository.AsignacionRutinaRepository;
import com.gymflow.backend.repository.AsistenciaRepository;
import com.gymflow.backend.repository.RutinaRepository;
import com.gymflow.backend.repository.SuscripcionRepository;
import com.gymflow.backend.repository.UsuarioRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceImportarTest {

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

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    private UsuarioService usuarioService;

    @BeforeEach
    void setUp() {
        usuarioService = new UsuarioService(
                usuarioRepository, codigoCarnetGenerator, passwordEncoder, validator,
                asistenciaRepository, asignacionRutinaRepository, rutinaRepository,
                asignacionEntrenadorRepository, suscripcionRepository);
    }

    private static ByteArrayInputStream csv(String contenido) {
        return new ByteArrayInputStream(contenido.getBytes(StandardCharsets.UTF_8));
    }

    private void stubGuardadoOk() {
        when(usuarioRepository.existsByTipoDocumentoAndNumeroDocumento(any(), any()))
                .thenReturn(false);
        when(codigoCarnetGenerator.generarUnico(any())).thenReturn("ABC123");
        when(passwordEncoder.encode(any())).thenReturn("hash");
    }

    private void stubEmailLibre() {
        when(usuarioRepository.existsByEmail(any())).thenReturn(false);
    }

    @Test
    void importaConPuntoYComa_yCreaAmbos() {
        stubEmailLibre();
        stubGuardadoOk();
        String contenido = "nombre;tipoDocumento;numeroDocumento;telefono;email\n"
                + "Ana García;CC;123;3001112222;ana@example.com\n"
                + "Bruno López;CE;456;;\n";

        ResultadoImportacionDTO resultado = usuarioService.importarSocios(csv(contenido), false);

        assertThat(resultado.totalFilas()).isEqualTo(2);
        assertThat(resultado.creados()).isEqualTo(2);
        assertThat(resultado.omitidos()).isEmpty();
        assertThat(resultado.errores()).isEmpty();
        verify(usuarioRepository, times(2)).save(any());
    }

    @Test
    void importaConComa() {
        stubEmailLibre();
        stubGuardadoOk();
        String contenido = "nombre,tipoDocumento,numeroDocumento,telefono,email\n"
                + "Ana García,CC,123,3001112222,ana@example.com\n";

        ResultadoImportacionDTO resultado = usuarioService.importarSocios(csv(contenido), false);

        assertThat(resultado.creados()).isEqualTo(1);
    }

    @Test
    void duplicadoContraBd_quedaOmitido() {
        when(usuarioRepository.existsByEmail("ana@example.com")).thenReturn(false);
        when(usuarioRepository.existsByTipoDocumentoAndNumeroDocumento("CC", "123"))
                .thenReturn(true);

        String contenido = "nombre,tipoDocumento,numeroDocumento,telefono,email\n"
                + "Ana García,CC,123,,ana@example.com\n";

        ResultadoImportacionDTO resultado = usuarioService.importarSocios(csv(contenido), false);

        assertThat(resultado.creados()).isZero();
        assertThat(resultado.omitidos()).hasSize(1);
        assertThat(resultado.omitidos().get(0).fila()).isEqualTo(2);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void duplicadoDentroDelArchivo_quedaOmitido() {
        stubEmailLibre();
        stubGuardadoOk();
        String contenido = "nombre,tipoDocumento,numeroDocumento,telefono,email\n"
                + "Ana García,CC,123,,ana@example.com\n"
                + "Ana Copia,CC,123,,\n";

        ResultadoImportacionDTO resultado = usuarioService.importarSocios(csv(contenido), false);

        assertThat(resultado.creados()).isEqualTo(1);
        assertThat(resultado.omitidos()).hasSize(1);
        assertThat(resultado.omitidos().get(0).motivo()).contains("archivo");
        verify(usuarioRepository, times(1)).save(any());
    }

    @Test
    void filaInvalida_quedaEnErroresYSigueConLasDemás() {
        stubGuardadoOk();
        String contenido = "nombre,tipoDocumento,numeroDocumento,telefono,email\n"
                + "Sin Tipo,XX,123,,\n"
                + "Ana García,CC,124,,\n";

        ResultadoImportacionDTO resultado = usuarioService.importarSocios(csv(contenido), false);

        assertThat(resultado.creados()).isEqualTo(1);
        assertThat(resultado.errores()).hasSize(1);
        assertThat(resultado.errores().get(0).fila()).isEqualTo(2);
    }

    @Test
    void dryRun_validaSinGuardar() {
        when(usuarioRepository.existsByEmail("ana@example.com")).thenReturn(false);
        when(usuarioRepository.existsByTipoDocumentoAndNumeroDocumento("CC", "123"))
                .thenReturn(false);
        String contenido = "nombre,tipoDocumento,numeroDocumento,telefono,email\n"
                + "Ana García,CC,123,,ana@example.com\n";

        ResultadoImportacionDTO resultado = usuarioService.importarSocios(csv(contenido), true);

        assertThat(resultado.creados()).isEqualTo(1);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void dryRun_detectaDuplicadoSinGuardar() {
        when(usuarioRepository.existsByTipoDocumentoAndNumeroDocumento("CC", "123"))
                .thenReturn(true);
        String contenido = "nombre,tipoDocumento,numeroDocumento,telefono,email\n"
                + "Ana García,CC,123,,\n";

        ResultadoImportacionDTO resultado = usuarioService.importarSocios(csv(contenido), true);

        assertThat(resultado.creados()).isZero();
        assertThat(resultado.omitidos()).hasSize(1);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void bomInicial_noRomegaElHeader() {
        stubGuardadoOk();
        String contenido = "\uFEFFnombre,tipoDocumento,numeroDocumento,telefono,email\n"
                + "Ana García,CC,123,,\n";

        ResultadoImportacionDTO resultado = usuarioService.importarSocios(csv(contenido), false);

        assertThat(resultado.creados()).isEqualTo(1);
    }

    @Test
    void headerInvalido_lanza400() {
        String contenido = "nombre,documento\nAna,123\n";

        assertThatThrownBy(() -> usuarioService.importarSocios(csv(contenido), false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No se pudo importar");
    }

    @Test
    void archivoVacio_lanza400() {
        assertThatThrownBy(() -> usuarioService.importarSocios(csv(""), false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No se pudo importar");
    }

    @Test
    void masDe500Filas_lanza400() {
        StringBuilder contenido = new StringBuilder(
                "nombre,tipoDocumento,numeroDocumento,telefono,email\n");
        for (int i = 0; i < 501; i++) {
            contenido.append("Socio ").append(i).append(",CC,").append(1000 + i).append(",,\n");
        }

        assertThatThrownBy(() -> usuarioService.importarSocios(csv(contenido.toString()), false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No se pudo importar");
        verify(usuarioRepository, never()).save(any());
    }
}
