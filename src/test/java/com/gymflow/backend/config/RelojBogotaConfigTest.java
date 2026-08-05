package com.gymflow.backend.config;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 5, regla 7: la única fuente de "hoy" del proyecto es el bean Clock
 * con zona America/Bogota. Pita si alguien cambia la zona — un "hoy"
 * equivocado rompe la regla 1/día y el fix de TZ de SuscripcionService.
 */
class RelojBogotaConfigTest {

    @Test
    void bean_relojConZonaDeBogota() {
        var config = new RelojBogotaConfig();
        assertThat(config.relojBogota().getZone()).isEqualTo(ZoneId.of("America/Bogota"));
    }
}