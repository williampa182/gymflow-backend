package com.gymflow.backend.service;

import com.gymflow.backend.client.EmailClient;
import com.gymflow.backend.client.EmailEnvioException;
import com.gymflow.backend.client.EmailPayload;
import com.gymflow.backend.model.Plan;
import com.gymflow.backend.model.Suscripcion;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.EstadoSuscripcion;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.model.enums.TipoPlan;
import com.gymflow.backend.repository.SuscripcionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacionVencimientoServiceTest {

    private static final String CTA_BASE_URL = "http://localhost:3000";
    private static final String FROM = "no-reply@gymflow.com";
    private static final Clock RELOJ_BOGOTA =
            Clock.fixed(LocalDate.parse("2026-08-06").atTime(10, 0).atZone(ZoneId.of("America/Bogota")).toInstant(),
                    ZoneId.of("America/Bogota"));

    @Mock
    private SuscripcionRepository suscripcionRepository;

    @Mock
    private EmailClient emailClient;

    private NotificacionVencimientoService notificacionVencimientoService;

    @BeforeEach
    void setUp() {
        notificacionVencimientoService = new NotificacionVencimientoService(
                suscripcionRepository, emailClient, RELOJ_BOGOTA, 7, CTA_BASE_URL, FROM);
    }

    private Usuario usuarioActivo(String email) {
        return Usuario.builder()
                .id(1L)
                .nombre("Cliente Test")
                .email(email)
                .password("hashed")
                .rol(Rol.CLIENTE)
                .activo(true)
                .build();
    }

    private Plan planMensual() {
        return Plan.builder()
                .id(1L)
                .nombre("Plan Mensual")
                .precio(new BigDecimal("50000"))
                .duracionDias(30)
                .tipo(TipoPlan.MENSUAL)
                .activo(true)
                .build();
    }

    private Suscripcion activaPorVencer(Long id, Usuario usuario, Plan plan) {
        return Suscripcion.builder()
                .id(id)
                .usuario(usuario)
                .plan(plan)
                .fechaInicio(LocalDate.now(RELOJ_BOGOTA))
                .fechaFin(LocalDate.now(RELOJ_BOGOTA).plusDays(3))
                .estado(EstadoSuscripcion.ACTIVA)
                .build();
    }

    @Test
    void procesarVencimientos_seleccionaPendientesMarcaNotificadoEnYGuarda() {
        Usuario usuario = usuarioActivo("cliente@gymflow.com");
        Plan plan = planMensual();
        Suscripcion suscripcion = activaPorVencer(1L, usuario, plan);

        when(suscripcionRepository.findPendientesAvisoVencimiento(
                eq(EstadoSuscripcion.ACTIVA), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(suscripcion));

        int enviadas = notificacionVencimientoService.procesarVencimientos();

        assertThat(enviadas).isEqualTo(1);
        assertThat(suscripcion.getNotificadoEn()).isNotNull();

        ArgumentCaptor<LocalDate> desde = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> hasta = ArgumentCaptor.forClass(LocalDate.class);
        verify(suscripcionRepository).findPendientesAvisoVencimiento(
                eq(EstadoSuscripcion.ACTIVA), desde.capture(), hasta.capture());
        assertThat(desde.getValue()).isEqualTo(LocalDate.now(RELOJ_BOGOTA));
        assertThat(hasta.getValue()).isEqualTo(LocalDate.now(RELOJ_BOGOTA).plusDays(7));
        verify(suscripcionRepository).save(suscripcion);
    }

    @Test
    void procesarVencimientos_envioFallidoNoMarcaYSigueConElSiguiente() {
        Usuario usuarioUno = usuarioActivo("cliente-uno@gymflow.com");
        Usuario usuarioDos = usuarioActivo("cliente-dos@gymflow.com");
        Plan plan = planMensual();
        Suscripcion falla = activaPorVencer(1L, usuarioUno, plan);
        Suscripcion ok = activaPorVencer(2L, usuarioDos, plan);

        when(suscripcionRepository.findPendientesAvisoVencimiento(
                eq(EstadoSuscripcion.ACTIVA), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(falla, ok));
        // La primera llamada falla; la segunda (ok) hace nothing (default).
        doThrow(new EmailEnvioException("La API de Resend devolvió un error (401)", null))
                .doNothing()
                .when(emailClient).enviar(any(EmailPayload.class));

        int enviadas = notificacionVencimientoService.procesarVencimientos();

        assertThat(enviadas).isEqualTo(1);
        assertThat(falla.getNotificadoEn()).isNull();
        assertThat(ok.getNotificadoEn()).isNotNull();
        verify(suscripcionRepository).save(ok);
        verify(suscripcionRepository, never()).save(falla);
    }

    @Test
    void procesarVencimientos_sustituyeElPlaceholderDeLaCtaEnHtmlYTexto() {
        Usuario usuario = usuarioActivo("cliente@gymflow.com");
        Plan plan = planMensual();
        Suscripcion suscripcion = activaPorVencer(1L, usuario, plan);

        when(suscripcionRepository.findPendientesAvisoVencimiento(
                eq(EstadoSuscripcion.ACTIVA), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(suscripcion));

        notificacionVencimientoService.procesarVencimientos();

        ArgumentCaptor<EmailPayload> payload = ArgumentCaptor.forClass(EmailPayload.class);
        verify(emailClient).enviar(payload.capture());
        EmailPayload email = payload.getValue();
        String cta = CTA_BASE_URL + "/dashboard/suscripciones";

        assertThat(email.html()).contains(cta).doesNotContain("${CTA_URL}");
        assertThat(email.text()).contains(cta).doesNotContain("${CTA_URL}");
        assertThat(email.from()).isEqualTo(FROM);
        assertThat(email.to()).containsExactly("cliente@gymflow.com");
    }

    @Test
    void procesarVencimientos_laCtaNoQuedaConDobleSlashSiLaBaseTerminaEnSlash() {
        notificacionVencimientoService = new NotificacionVencimientoService(
                suscripcionRepository, emailClient, RELOJ_BOGOTA, 7, "http://localhost:3000/", FROM);
        Usuario usuario = usuarioActivo("cliente@gymflow.com");
        Plan plan = planMensual();
        Suscripcion suscripcion = activaPorVencer(1L, usuario, plan);

        when(suscripcionRepository.findPendientesAvisoVencimiento(
                eq(EstadoSuscripcion.ACTIVA), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(suscripcion));

        notificacionVencimientoService.procesarVencimientos();

        ArgumentCaptor<EmailPayload> payload = ArgumentCaptor.forClass(EmailPayload.class);
        verify(emailClient).enviar(payload.capture());
        assertThat(payload.getValue().html())
                .contains("http://localhost:3000/dashboard/suscripciones")
                .doesNotContain("http://localhost:3000//dashboard/suscripciones");
    }
}
