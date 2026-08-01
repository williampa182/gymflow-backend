package com.gymflow.backend.service;

import com.gymflow.backend.client.EmailClient;
import com.gymflow.backend.client.EmailPayload;
import com.gymflow.backend.model.Plan;
import com.gymflow.backend.model.Suscripcion;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.EstadoSuscripcion;
import com.gymflow.backend.repository.SuscripcionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Aviso de vencimiento de suscripciones por email. Ventana abierta con
 * tracking (columna notificado_en) en vez de one-shot: una suscripción se
 * notifica la primera corrida en la que cae dentro de
 * [hoy, hoy+ventanaDias] y aún no fue notificada. Si el job se pierde un
 * día, la suscripción que quedó dentro de la ventana se sigue notificando
 * el día siguiente.
 *
 * La CTA ("Renovar ahora") sale de la plantilla con el placeholder
 * ${CTA_URL}, que se sustituye acá (aguas abajo de la plantilla) con la base
 * configurable + /dashboard/suscripciones — ver PlantillaEmailVencimiento.
 */
@Service
public class NotificacionVencimientoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionVencimientoService.class);

    private static final String ASUNTO = "Tu plan de GymFlow está por vencer";
    private static final String CTA_PLACEHOLDER = "${CTA_URL}";
    private static final String CTA_PATH = "/dashboard/suscripciones";

    private final SuscripcionRepository suscripcionRepository;
    private final EmailClient emailClient;
    private final int ventanaDias;
    private final String ctaBaseUrl;
    private final String from;

    public NotificacionVencimientoService(
            SuscripcionRepository suscripcionRepository,
            EmailClient emailClient,
            @Value("${app.email.aviso-ventana-dias:7}") int ventanaDias,
            @Value("${app.email.cta-base-url:http://localhost:3000}") String ctaBaseUrl,
            @Value("${app.email.from:no-reply@gymflow.com}") String from
    ) {
        this.suscripcionRepository = suscripcionRepository;
        this.emailClient = emailClient;
        this.ventanaDias = ventanaDias;
        this.ctaBaseUrl = ctaBaseUrl;
        this.from = from;
    }

    /**
     * Procesa los vencimientos de hoy: envía el email de aviso a cada
     * suscripción ACTIVA sin notificar dentro de la ventana y marca
     * notificadoEn solo cuando el envío fue exitoso.
     *
     * @return cantidad de notificaciones enviadas
     */
    @Transactional
    public int procesarVencimientos() {
        LocalDate hoy = LocalDate.now();
        LocalDate hasta = hoy.plusDays(ventanaDias);
        List<Suscripcion> pendientes = suscripcionRepository.findPendientesAvisoVencimiento(
                EstadoSuscripcion.ACTIVA, hoy, hasta);

        String cta = ctaBaseUrl.replaceAll("/+$", "") + CTA_PATH;
        int enviadas = 0;

        for (Suscripcion suscripcion : pendientes) {
            try {
                Usuario usuario = suscripcion.getUsuario();
                Plan plan = suscripcion.getPlan();
                String fechaVencimiento = suscripcion.getFechaFin().toString();
                String html = PlantillaEmailVencimiento.generarHtml(
                        usuario.getNombre(), plan.getNombre(), fechaVencimiento)
                        .replace(CTA_PLACEHOLDER, cta);
                String texto = PlantillaEmailVencimiento.generarTextoPlano(
                        usuario.getNombre(), plan.getNombre(), fechaVencimiento)
                        .replace(CTA_PLACEHOLDER, cta);

                emailClient.enviar(new EmailPayload(from, List.of(usuario.getEmail()), ASUNTO, html, texto));
                suscripcion.setNotificadoEn(LocalDateTime.now());
                suscripcionRepository.save(suscripcion);
                enviadas++;
            } catch (Exception ex) {
                // No se marca la suscripción: el reintento queda para la
                // próxima corrida del job. Un envío fallido no debe tumbar
                // el resto del lote.
                log.warn("No se pudo notificar el vencimiento de la suscripción {} (usuario id {}): {}",
                        suscripcion.getId(), suscripcion.getUsuario().getId(), ex.getMessage());
            }
        }

        return enviadas;
    }
}
