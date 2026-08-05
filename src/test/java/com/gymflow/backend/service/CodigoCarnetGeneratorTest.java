package com.gymflow.backend.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodigoCarnetGeneratorTest {

    private final CodigoCarnetGenerator generador = new CodigoCarnetGenerator();

    @Test
    void generar_siempreDevuelve6A8CaracteresDelAlfabeto() {
        for (int i = 0; i < 300; i++) {
            assertThat(generador.generar()).matches("[" + CodigoCarnetGenerator.ALFABETO + "]{6,8}");
        }
    }

    @Test
    void generar_produceCodigosVariados() {
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            vistos.add(generador.generar());
        }
        assertThat(vistos).hasSize(50);
    }

    @Test
    void generarUnico_codigoLibre_devuelveEnElPrimerIntento() {
        int[] consultas = {0};

        String codigo = generador.generarUnico(s -> {
            consultas[0]++;
            return false;
        });

        assertThat(consultas[0]).isEqualTo(1);
        assertThat(codigo).matches("[" + CodigoCarnetGenerator.ALFABETO + "]{6,8}");
    }

    @Test
    void generarUnico_aceptaLibreCuandoElPredicadoPasaAFalso() {
        int[] consultas = {0};

        String codigo = generador.generarUnico(s -> consultas[0]++ < 3);

        assertThat(consultas[0]).isEqualTo(4);
        assertThat(codigo).matches("[" + CodigoCarnetGenerator.ALFABETO + "]{6,8}");
    }

    @Test
    void generarUnico_agotaReintentosYLanzaCuandoSiempreOcupado() {
        assertThatThrownBy(() -> generador.generarUnico(ocupado -> true))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("No se pudo generar un codigo de carnet unico");
    }
}