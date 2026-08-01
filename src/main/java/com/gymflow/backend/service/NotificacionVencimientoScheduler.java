package com.gymflow.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job diario de aviso de vencimientos. Separado del servicio a propósito
 * para poder apagar el scheduling en tests vía
 * app.email.aviso-enabled=false (default: activo).
 */
@Component
@ConditionalOnProperty(name = "app.email.aviso-enabled", havingValue = "true", matchIfMissing = true)
public class NotificacionVencimientoScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificacionVencimientoScheduler.class);

    private final NotificacionVencimientoService notificacionVencimientoService;

    public NotificacionVencimientoScheduler(NotificacionVencimientoService notificacionVencimientoService) {
        this.notificacionVencimientoService = notificacionVencimientoService;
    }

    @Scheduled(cron = "${app.email.aviso-cron:0 0 9 * * *}")
    public void avisarVencimientos() {
        int enviadas = notificacionVencimientoService.procesarVencimientos();
        log.info("Aviso de vencimiento diario: {} notificaciones enviadas", enviadas);
    }
}
