package com.gymflow.backend.service;

import com.gymflow.backend.client.EmailClient;
import com.gymflow.backend.model.Plan;
import com.gymflow.backend.model.Suscripcion;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.EstadoSuscripcion;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.repository.SuscripcionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificacionVencimientoSinEmailTest {

    @Mock
    private SuscripcionRepository suscripcionRepository;

    @Mock
    private EmailClient emailClient;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-10-03T12:00:00Z"), ZoneId.of("America/Bogota"));

    private NotificacionVencimientoService service;

    @BeforeEach
    void setUp() {
        service = new NotificacionVencimientoService(
                suscripcionRepository, emailClient, clock, 7,
                "https://gymflow-frontend-ten.vercel.app", "no-reply@gymflow.com");
    }

    private Suscripcion suscripcionDe(String email) {
        Usuario usuario = Usuario.builder()
                .id(1L)
                .nombre("Socio")
                .email(email)
                .rol(Rol.CLIENTE)
                .build();
        Plan plan = Plan.builder().id(1L).nombre("Mensual").duracionDias(30).build();
        return Suscripcion.builder()
                .id(1L)
                .usuario(usuario)
                .plan(plan)
                .fechaInicio(LocalDate.now(clock))
                .fechaFin(LocalDate.now(clock).plusDays(3))
                .estado(EstadoSuscripcion.ACTIVA)
                .build();
    }

    @Test
    void socioSinEmail_seOmiteSinEnviarNiMarcar() {
        Suscripcion sinEmail = suscripcionDe(null);
        when(suscripcionRepository.findPendientesAvisoVencimiento(
                eq(EstadoSuscripcion.ACTIVA), any(), any()))
                .thenReturn(List.of(sinEmail));

        int enviadas = service.procesarVencimientos();

        assertThat(enviadas).isZero();
        verify(emailClient, never()).enviar(any());
        assertThat(sinEmail.getNotificadoEn()).isNull();
        verify(suscripcionRepository, never()).save(any());
    }

    @Test
    void socioConEmail_seNotificaYSeMarca() {
        Suscripcion conEmail = suscripcionDe("socio@gymflow.com");
        when(suscripcionRepository.findPendientesAvisoVencimiento(
                eq(EstadoSuscripcion.ACTIVA), any(), any()))
                .thenReturn(List.of(conEmail));

        int enviadas = service.procesarVencimientos();

        assertThat(enviadas).isEqualTo(1);
        verify(emailClient).enviar(any());
        assertThat(conEmail.getNotificadoEn()).isNotNull();
    }
}
