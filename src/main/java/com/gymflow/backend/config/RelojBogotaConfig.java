package com.gymflow.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Única fuente de "hoy" del proyecto (Fase 5, regla 7): Clock con zona
 * fija America/Bogota. Railway corre en UTC y el proyecto usaba
 * LocalDate.now() con la TZ del servidor, lo que hacía que "hoy" pudiera
 * ser el día equivocado para un cliente bogotano.
 *
 * TODO los puntos que usan "hoy" deben consumir este bean:
 *  - SuscripcionService.inscribir (ya migrado, fix del bug de TZ).
 *  - NotificacionVencimientoService.procesarVencimientos (pendiente, fuera
 *    de scope Fase 5).
 * Los tests usan Clock fijo (ej. Clock.fixed) en vez de este bean.
 */
@Configuration
public class RelojBogotaConfig {

    @Bean
    public Clock relojBogota() {
        return Clock.system(ZoneId.of("America/Bogota"));
    }
}
