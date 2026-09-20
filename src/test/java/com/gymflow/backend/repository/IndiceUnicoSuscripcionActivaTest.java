package com.gymflow.backend.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Paso 1 D1: el índice único parcial 001 debe existir en CUALQUIER base donde
 * corran los tests (local, CI). Hibernate no lo crea y el CI no corre
 * scripts/migrations, así que se aplica acá (idempotente) y se verifica.
 *
 * Si este test falla en rojo, la divergencia lab-vs-prod volvió: no seguir a
 * Paso 2 hasta ponerlo en verde.
 */
@SpringBootTest
@Sql(scripts = "/db/test-001-unique-suscripcion-activa.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class IndiceUnicoSuscripcionActivaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void indiceParcialExiste() {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes " +
                "WHERE schemaname = 'public' AND tablename = 'suscripciones' " +
                "AND indexname = 'uq_suscripcion_activa_por_usuario'",
                Integer.class);
        assertThat(n).as("001 debe estar aplicado donde corren los tests").isEqualTo(1);
    }

    @Test
    void sinDuplicadosActivaPreexistentes() {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM (SELECT usuario_id FROM suscripciones " +
                "WHERE estado = 'ACTIVA' GROUP BY usuario_id HAVING count(*) > 1) d",
                Integer.class);
        assertThat(n).as("ningún usuario con 2 ACTIVA").isEqualTo(0);
    }
}
