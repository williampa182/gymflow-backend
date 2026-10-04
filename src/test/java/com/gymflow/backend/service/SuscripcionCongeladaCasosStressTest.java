package com.gymflow.backend.service;

import com.gymflow.backend.dto.ResultadoImportacionDTO;
import com.gymflow.backend.dto.SuscripcionRequestDTO;
import com.gymflow.backend.dto.SuscripcionResponseDTO;
import com.gymflow.backend.model.Plan;
import com.gymflow.backend.model.Suscripcion;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.EstadoSuscripcion;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.model.enums.TipoPlan;
import com.gymflow.backend.repository.PlanRepository;
import com.gymflow.backend.repository.SuscripcionRepository;
import com.gymflow.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Estrés por DATOS (no por volumen) contra Postgres real: importa el
 * fixture socios-casos.csv y somete cada caso —activa, por vencer,
 * vencida, cancelada, congelada, congelada-vieja— a su flujo (check-in,
 * congelar/descongelar, crear-bloqueado, conteo, filtros).
 */
@SpringBootTest
@Sql(scripts = "/db/test-007-008-documento-congelada.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class SuscripcionCongeladaCasosStressTest {

    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private SuscripcionService suscripcionService;
    @Autowired
    private AsistenciaService asistenciaService;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PlanRepository planRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager txManager;

    private final List<Long> usuariosCreados = new ArrayList<>();
    private final List<Long> planesCreados = new ArrayList<>();
    private Plan plan;

    @BeforeEach
    void limpiarPrevio() {
        limpiar();
    }

    @AfterEach
    void limpiarPosterior() {
        limpiar();
    }

    private void limpiar() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(s -> {
            jdbcTemplate.update(
                    "delete from suscripciones where usuario_id in "
                            + "(select id from usuarios where email like 'datacaso-%')");
            jdbcTemplate.update("delete from asistencias where usuario_id in "
                    + "(select id from usuarios where email like 'datacaso-%')");
            jdbcTemplate.update("delete from usuarios where email like 'datacaso-%'");
        });
        usuariosCreados.clear();
        planesCreados.clear();
    }

    private Usuario porEmail(String email) {
        return usuarioRepository.findByEmail(email).orElseThrow();
    }

    private SuscripcionResponseDTO suscribir(String email, LocalDate inicio) {
        Usuario u = porEmail(email);
        usuariosCreados.add(u.getId());
        SuscripcionRequestDTO r = new SuscripcionRequestDTO();
        r.setUsuarioId(u.getId());
        r.setPlanId(plan.getId());
        r.setFechaInicio(inicio);
        return suscripcionService.crear(r);
    }

    @Test
    void casosDeDatos_comportamientoEsperado() throws Exception {
        LocalDate hoy = LocalDate.now();
        plan = planRepository.save(Plan.builder()
                .nombre("Plan Casos " + System.nanoTime())
                .precio(new BigDecimal("30000"))
                .duracionDias(30)
                .tipo(TipoPlan.MENSUAL)
                .activo(true)
                .build());
        planesCreados.add(plan.getId());

        try (InputStream csv = getClass().getResourceAsStream("/importacion/socios-casos.csv")) {
            ResultadoImportacionDTO importados = usuarioService.importarSocios(csv, false);
            assertThat(importados.creados()).isEqualTo(6);
            assertThat(importados.errores()).isEmpty();
        }

        // Activa vigente: entra.
        suscribir("datacaso-activa@test.com", hoy.minusDays(5));
        assertThat(asistenciaService.marcarMi("datacaso-activa@test.com").getId()).isNotNull();

        // Por vencer (2 días): sigue activa y entra.
        SuscripcionResponseDTO porVencer = suscribir("datacaso-porvencer@test.com", hoy.minusDays(28));
        assertThat(porVencer.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(asistenciaService.marcarMi("datacaso-porvencer@test.com").getId()).isNotNull();

        // Vencida de facto: derivada y no entra (mensaje genérico, no congelada).
        SuscripcionResponseDTO vencida = suscribir("datacaso-vencida@test.com", hoy.minusDays(40));
        assertThat(vencida.getEstado()).isEqualTo(EstadoSuscripcion.VENCIDA);
        assertThatThrownBy(() -> asistenciaService.marcarMi("datacaso-vencida@test.com"))
                .hasMessageContaining("plan activo")
                .hasMessageNotContaining("congelada");

        // Cancelada: no entra.
        SuscripcionResponseDTO cancelada = suscribir("datacaso-cancelado@test.com", hoy.minusDays(3));
        suscripcionService.cancelar(cancelada.getId());
        assertThatThrownBy(() -> asistenciaService.marcarMi("datacaso-cancelado@test.com"))
                .isInstanceOf(RuntimeException.class);

        // Congelada: no entra con mensaje propio; crear otra bloquea con 409.
        SuscripcionResponseDTO congelada = suscribir("datacaso-congelada@test.com", hoy.minusDays(5));
        suscripcionService.congelar(congelada.getId());
        assertThatThrownBy(() -> asistenciaService.marcarMi("datacaso-congelada@test.com"))
                .hasMessageContaining("congelada");
        SuscripcionRequestDTO otra = new SuscripcionRequestDTO();
        otra.setUsuarioId(porEmail("datacaso-congelada@test.com").getId());
        otra.setPlanId(plan.getId());
        otra.setFechaInicio(hoy);
        assertThatThrownBy(() -> suscripcionService.crear(otra))
                .hasMessageContaining("congelada");

        // Congelada vieja (20 días): al descongelar extiende +20 y vuelve activa.
        SuscripcionResponseDTO vieja = suscribir(
                "datacaso-congeladoviejo@test.com", hoy.minusDays(25));
        Long viejaId = vieja.getId();
        suscripcionService.congelar(viejaId);
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(status -> suscripcionRepository.findById(viejaId).ifPresent(s -> {
            s.setCongeladaDesde(hoy.minusDays(20));
            suscripcionRepository.save(s);
        }));
        LocalDate finAntes = suscripcionRepository.findById(viejaId).orElseThrow().getFechaFin();
        SuscripcionResponseDTO descongelada = suscripcionService.descongelar(viejaId);
        assertThat(descongelada.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(descongelada.getFechaFin()).isEqualTo(finAntes.plusDays(20));

        // Filtros y conteo ven la congelada.
        List<String> congeladas = suscripcionService
                .listarPorEstado(EstadoSuscripcion.CONGELADA, PageRequest.of(0, 50))
                .getContent().stream().map(SuscripcionResponseDTO::getNombreUsuario).toList();
        assertThat(congeladas).contains("Elena Congelada");
        assertThat(suscripcionService.contarPorEstado().congeladas()).isGreaterThanOrEqualTo(1);
    }
}
