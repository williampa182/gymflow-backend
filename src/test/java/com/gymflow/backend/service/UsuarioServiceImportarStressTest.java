package com.gymflow.backend.service;

import com.gymflow.backend.dto.ResultadoImportacionDTO;
import com.gymflow.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Estrés de la importación CSV contra Postgres real (sin mocks): volumen
 * máximo, concurrencia, binario y encoding ajeno. Limpieza manual por
 * prefijo (los hilos concurrently no heredan la transacción del test).
 */
@SpringBootTest
@Sql(scripts = "/db/test-007-008-documento-congelada.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class UsuarioServiceImportarStressTest {

    private static final String HEADER = "nombre,tipoDocumento,numeroDocumento,telefono,email\n";

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void limpiarEstres() {
        jdbcTemplate.update("delete from usuarios where email like 'stress-%'");
        jdbcTemplate.update("delete from usuarios where numero_documento like 'STRESS-%'");
    }

    private static ByteArrayInputStream csv(String contenido) {
        return new ByteArrayInputStream(contenido.getBytes(StandardCharsets.UTF_8));
    }

    private static String filaSocio(int i, String prefijoDoc) {
        return "Socio " + i + ",CC," + prefijoDoc + i + ",,stress-" + i + "@test.com\n";
    }

    @Test
    void importa500Filas_enTiempoRazonable() {
        StringBuilder contenido = new StringBuilder(HEADER);
        for (int i = 0; i < 500; i++) {
            contenido.append(filaSocio(i, "900"));
        }

        long inicio = System.currentTimeMillis();
        ResultadoImportacionDTO resultado =
                usuarioService.importarSocios(csv(contenido.toString()), false);
        long segundos = (System.currentTimeMillis() - inicio) / 1000;

        assertThat(resultado.totalFilas()).isEqualTo(500);
        assertThat(resultado.creados()).isEqualTo(500);
        assertThat(resultado.omitidos()).isEmpty();
        assertThat(resultado.errores()).isEmpty();
        assertThat(segundos).isLessThan(300);
    }

    @Test
    void importConcurrente_mismoArchivo_sinDuplicadosNiExcepciones() throws Exception {
        StringBuilder contenido = new StringBuilder(HEADER);
        for (int i = 0; i < 50; i++) {
            contenido.append(filaSocio(i, "910"));
        }
        byte[] bytes = contenido.toString().getBytes(StandardCharsets.UTF_8);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<ResultadoImportacionDTO>> futuros = new ArrayList<>();
            for (int t = 0; t < 2; t++) {
                futuros.add(pool.submit(() ->
                        usuarioService.importarSocios(new ByteArrayInputStream(bytes), false)));
            }
            int creadosTotales = 0;
            for (Future<ResultadoImportacionDTO> futuro : futuros) {
                creadosTotales += futuro.get(5, TimeUnit.MINUTES).creados();
            }
            assertThat(creadosTotales).isEqualTo(50);
            Long enBd = jdbcTemplate.queryForObject(
                    "select count(*) from usuarios where numero_documento like '910%'", Long.class);
            assertThat(enBd).isEqualTo(50L);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void archivoBinario_fallaControlado400() {
        byte[] basura = new byte[512];
        new SecureRandom().nextBytes(basura);

        assertThatThrownBy(() ->
                usuarioService.importarSocios(new ByteArrayInputStream(basura), false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No se pudo importar");
    }

    @Test
    void latin1_noTumba_soloMojibake() {
        String fila = "José Núñez,CC,920001,,stress-latin1@test.com\n";
        byte[] latin1 = (HEADER + fila).getBytes(Charset.forName("windows-1252"));

        ResultadoImportacionDTO resultado =
                usuarioService.importarSocios(new ByteArrayInputStream(latin1), false);

        assertThat(resultado.totalFilas()).isEqualTo(1);
        assertThat(resultado.creados()).isEqualTo(1);
    }
}
