package com.gymflow.backend.repository;

import com.gymflow.backend.client.EmailClient;
import com.gymflow.backend.model.Plan;
import com.gymflow.backend.model.Suscripcion;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.EstadoSuscripcion;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.model.enums.TipoPlan;
import com.gymflow.backend.service.NotificacionVencimientoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test del trigger "quién recibe hoy" del aviso de vencimiento contra
 * Postgres real: la matriz de selección de findPendientesAvisoVencimiento
 * y el marcado de notificadoEn en NotificacionVencimientoService.
 *
 * Requiere Postgres/Redis reales (los @SpringBootTest de este proyecto los
 * corren como services de Docker en CI; en local hace falta el
 * docker-compose levantado). El EmailClient se mockea para no hacer
 * llamadas HTTP reales, y aviso-enabled=false apaga el job programado.
 */
@SpringBootTest
@TestPropertySource(properties = "app.email.aviso-enabled=false")
@Transactional
class SuscripcionPendientesAvisoRepositoryTest {

    @Autowired
    private NotificacionVencimientoService notificacionVencimientoService;

    @Autowired
    private SuscripcionRepository suscripcionRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PlanRepository planRepository;

    @MockitoBean
    private EmailClient emailClient;

    @Test
    void matrizQuienRecibeHoy_marcaSoloALosQueReciben() {
        LocalDate hoy = LocalDate.now();
        LocalDateTime notificadoPrevio = LocalDateTime.now().minusDays(1);
        Plan plan = crearPlan();

        // La DB local puede tener suscripciones ACTIVA en ventana sembradas de
        // antes (dev/CI), que legítimamente también reciben. Se mide el baseline
        // antes de sembrar para poder assertar un conteo exacto sin depender de
        // que la DB esté vacía.
        int baselinePendientes = suscripcionRepository.findPendientesAvisoVencimiento(
                EstadoSuscripcion.ACTIVA, hoy, hoy.plusDays(7)).size();

        Suscripcion activaEnVentanaSinNotificar = crearSuscripcion(
                crearUsuario("aviso-recibe@gymflow.com", true), plan,
                EstadoSuscripcion.ACTIVA, hoy.plusDays(3), null);
        Suscripcion activaEnVentanaYaNotificada = crearSuscripcion(
                crearUsuario("aviso-ya-notificado@gymflow.com", true), plan,
                EstadoSuscripcion.ACTIVA, hoy.plusDays(3), notificadoPrevio);
        Suscripcion activaFueraDeVentana = crearSuscripcion(
                crearUsuario("aviso-fuera-ventana@gymflow.com", true), plan,
                EstadoSuscripcion.ACTIVA, hoy.plusDays(30), null);
        Suscripcion canceladaEnVentana = crearSuscripcion(
                crearUsuario("aviso-cancelada@gymflow.com", true), plan,
                EstadoSuscripcion.CANCELADA, hoy.plusDays(3), null);
        Suscripcion activaConUsuarioInactivo = crearSuscripcion(
                crearUsuario("aviso-inactivo@gymflow.com", false), plan,
                EstadoSuscripcion.ACTIVA, hoy.plusDays(3), null);
        Suscripcion activaBordeHoy = crearSuscripcion(
                crearUsuario("aviso-borde@gymflow.com", true), plan,
                EstadoSuscripcion.ACTIVA, hoy, null);

        int enviadas = notificacionVencimientoService.procesarVencimientos();

        assertThat(enviadas).isEqualTo(baselinePendientes + 2);
        assertThat(activaEnVentanaSinNotificar.getNotificadoEn()).isNotNull();
        assertThat(activaBordeHoy.getNotificadoEn()).isNotNull();
        assertThat(activaEnVentanaYaNotificada.getNotificadoEn())
                .as("la ya notificada no se debe renotificar ni pisar su marca")
                .isEqualTo(notificadoPrevio);
        assertThat(activaFueraDeVentana.getNotificadoEn()).isNull();
        assertThat(canceladaEnVentana.getNotificadoEn()).isNull();
        assertThat(activaConUsuarioInactivo.getNotificadoEn()).isNull();
    }

    private Usuario crearUsuario(String email, boolean activo) {
        return usuarioRepository.save(Usuario.builder()
                .nombre("Cliente Aviso")
                .email(email)
                .password("hashed-password")
                .rol(Rol.CLIENTE)
                .activo(activo)
                .build());
    }

    private Plan crearPlan() {
        return planRepository.save(Plan.builder()
                .nombre("Plan Aviso " + System.nanoTime())
                .precio(new BigDecimal("50000"))
                .duracionDias(30)
                .tipo(TipoPlan.MENSUAL)
                .activo(true)
                .build());
    }

    private Suscripcion crearSuscripcion(Usuario usuario, Plan plan, EstadoSuscripcion estado,
                                         LocalDate fechaFin, LocalDateTime notificadoEn) {
        // fechaInicio se deriva para que @PrePersist (fechaInicio + duracionDias)
        // recompute exactamente la fechaFin pedida.
        return suscripcionRepository.save(Suscripcion.builder()
                .usuario(usuario)
                .plan(plan)
                .fechaInicio(fechaFin.minusDays(plan.getDuracionDias()))
                .fechaFin(fechaFin)
                .estado(estado)
                .notificadoEn(notificadoEn)
                .build());
    }
}
