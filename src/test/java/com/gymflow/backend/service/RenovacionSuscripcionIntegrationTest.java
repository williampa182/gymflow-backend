package com.gymflow.backend.service;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Paso 2 D1-A contra Postgres REAL con el índice 001 aplicado (vía @Sql,
 * idempotente): vigencia derivada por fecha, renovación transaccional
 * (vieja→VENCIDA + nueva ACTIVA) y su concurrencia.
 *
 * Sin @Transactional a nivel de clase a propósito: el test de concurrencia
 * necesita commits reales por hilo. Limpieza manual en @AfterEach con emails
 * únicos por corrida.
 */
@SpringBootTest
@Sql(scripts = "/db/test-001-unique-suscripcion-activa.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class RenovacionSuscripcionIntegrationTest {

    @Autowired
    private SuscripcionService suscripcionService;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PlanRepository planRepository;

    private final List<Long> usuariosCreados = new ArrayList<>();

    @Autowired
    private PlatformTransactionManager txManager;

    @AfterEach
    void limpiar() {
        // Sin @Transactional a nivel de test (el test concurrente necesita
        // commits reales): la limpieza abre su propia transacción por programa.
        // @Transactional en @AfterEach NO inicia transacción (solo se une a la
        // del test), verificado con el error "No EntityManager with actual
        // transaction" — por eso TransactionTemplate explícito.
        TransactionTemplate tx = new TransactionTemplate(txManager);
        for (Long id : usuariosCreados) {
            final Long uid = id;
            tx.executeWithoutResult(s -> {
                suscripcionRepository.deleteByUsuarioId(uid);
                usuarioRepository.deleteById(uid);
            });
        }
        usuariosCreados.clear();
    }

    @Test
    void filtroVencida_traeAyer_noTraeHoyNiManana() {
        LocalDate hoy = LocalDate.now();
        Plan plan = crearPlan();
        Usuario uAyer = crearUsuario();
        Usuario uHoy = crearUsuario();
        Usuario uManana = crearUsuario();
        guardarFisica(uAyer, plan, EstadoSuscripcion.ACTIVA, hoy.minusDays(1));
        guardarFisica(uHoy, plan, EstadoSuscripcion.ACTIVA, hoy);
        guardarFisica(uManana, plan, EstadoSuscripcion.ACTIVA, hoy.plusDays(1));

        List<String> vencidas = suscripcionService
                .listarPorEstado(EstadoSuscripcion.VENCIDA, PageRequest.of(0, 50))
                .getContent().stream()
                .map(SuscripcionResponseDTO::getNombreUsuario).toList();
        List<String> activas = suscripcionService
                .listarPorEstado(EstadoSuscripcion.ACTIVA, PageRequest.of(0, 50))
                .getContent().stream()
                .map(SuscripcionResponseDTO::getNombreUsuario).toList();

        assertThat(vencidas).contains(uAyer.getNombre());
        assertThat(vencidas).doesNotContain(uHoy.getNombre(), uManana.getNombre());
        assertThat(activas).contains(uHoy.getNombre(), uManana.getNombre());
        assertThat(activas).doesNotContain(uAyer.getNombre());
    }

    @Test
    void renovarVencida_creaNuevaYTransicionaVieja() {
        LocalDate hoy = LocalDate.now();
        Plan plan = crearPlan();
        Usuario u = crearUsuario();
        Suscripcion vieja = guardarFisica(u, plan, EstadoSuscripcion.ACTIVA, hoy.minusDays(1));

        SuscripcionResponseDTO nueva = suscripcionService.crear(request(u.getId(), plan.getId(), hoy));

        assertThat(nueva.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(suscripcionRepository.findById(vieja.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoSuscripcion.VENCIDA);
    }

    @Test
    void renovarVigente_409YNoTocaNada() {
        LocalDate hoy = LocalDate.now();
        Plan plan = crearPlan();
        Usuario u = crearUsuario();
        Suscripcion vigente = guardarFisica(u, plan, EstadoSuscripcion.ACTIVA, hoy.plusDays(10));

        assertThatThrownBy(() -> suscripcionService.crear(request(u.getId(), plan.getId(), hoy)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ya tiene una suscripción activa");
        assertThat(suscripcionRepository.findById(vigente.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoSuscripcion.ACTIVA);
    }

    @Test
    void renovacionConcurrente_unoGanaOtro409() throws Exception {
        LocalDate hoy = LocalDate.now();
        Plan plan = crearPlan();
        Usuario u = crearUsuario();
        guardarFisica(u, plan, EstadoSuscripcion.ACTIVA, hoy.minusDays(1));

        CountDownLatch salida = new CountDownLatch(1);
        ConcurrentLinkedQueue<Object> resultados = new ConcurrentLinkedQueue<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = List.of(
                    pool.submit(() -> {
                        try {
                            salida.await(10, TimeUnit.SECONDS);
                            resultados.add(suscripcionService.crear(request(u.getId(), plan.getId(), hoy)));
                        } catch (Exception e) {
                            resultados.add(e);
                        }
                    }),
                    pool.submit(() -> {
                        try {
                            salida.await(10, TimeUnit.SECONDS);
                            resultados.add(suscripcionService.crear(request(u.getId(), plan.getId(), hoy)));
                        } catch (Exception e) {
                            resultados.add(e);
                        }
                    }));
            salida.countDown();
            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        long exitos = resultados.stream().filter(SuscripcionResponseDTO.class::isInstance).count();
        long fallos = resultados.stream().filter(RuntimeException.class::isInstance).count();
        assertThat(exitos).as("exactamente una renovación gana; resultados=%s", resultados).isEqualTo(1);
        assertThat(fallos).as("la otra falla limpio (409 u optimistic lock)").isEqualTo(1);
        assertThat(suscripcionRepository.findByUsuarioIdAndEstado(u.getId(), EstadoSuscripcion.ACTIVA))
                .as("como mucho una ACTIVA tras la carrera").isPresent();
    }

    private SuscripcionRequestDTO request(Long usuarioId, Long planId, LocalDate inicio) {
        SuscripcionRequestDTO r = new SuscripcionRequestDTO();
        r.setUsuarioId(usuarioId);
        r.setPlanId(planId);
        r.setFechaInicio(inicio);
        return r;
    }

    private Usuario crearUsuario() {
        String tag = "renov-" + System.nanoTime();
        Usuario u = usuarioRepository.save(Usuario.builder()
                .nombre("Cliente " + tag)
                .email(tag + "@gymflow.test")
                .password("x")
                .rol(Rol.CLIENTE)
                .build());
        usuariosCreados.add(u.getId());
        return u;
    }

    private Plan crearPlan() {
        return planRepository.save(Plan.builder()
                .nombre("Plan Renov " + System.nanoTime())
                .precio(new BigDecimal("30000"))
                .duracionDias(30)
                .tipo(TipoPlan.MENSUAL)
                .activo(true)
                .build());
    }

    private Suscripcion guardarFisica(Usuario u, Plan plan, EstadoSuscripcion estado, LocalDate fechaFin) {
        return suscripcionRepository.save(Suscripcion.builder()
                .usuario(u)
                .plan(plan)
                .fechaInicio(fechaFin.minusDays(plan.getDuracionDias()))
                .fechaFin(fechaFin)
                .estado(estado)
                .build());
    }
}
