package com.gymflow.backend.controller;

import com.gymflow.backend.dto.KioscoConfigResponseDTO;
import com.gymflow.backend.dto.KioscoKeyResponseDTO;
import com.gymflow.backend.service.KioscoConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KioscoConfigControllerTest {

    @Mock
    private KioscoConfigService kioscoConfigService;

    @InjectMocks
    private KioscoConfigController kioscoConfigController;

    @Test
    void configuracion_devuelve200ConElBooleanoSinExponerLaKey() {
        when(kioscoConfigService.configurada()).thenReturn(true);

        ResponseEntity<KioscoConfigResponseDTO> respuesta = kioscoConfigController.configuracion();

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody().isConfigurada()).isTrue();
        verify(kioscoConfigService).configurada();
    }

    @Test
    void configuracion_devuelveFalseCuandoNoHayClaveSembrada() {
        when(kioscoConfigService.configurada()).thenReturn(false);

        ResponseEntity<KioscoConfigResponseDTO> respuesta = kioscoConfigController.configuracion();

        assertThat(respuesta.getBody().isConfigurada()).isFalse();
    }

    @Test
    void rotar_devuelve200ConLaKeyNuevaEnTextoPlano() {
        when(kioscoConfigService.rotar()).thenReturn("clave-nueva-generada");

        ResponseEntity<KioscoKeyResponseDTO> respuesta = kioscoConfigController.rotar();

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody().getKey()).isEqualTo("clave-nueva-generada");
        verify(kioscoConfigService).rotar();
    }

    @Test
    void configuracionYTocar_tienenPreAuthorizeSoloAdmin() throws NoSuchMethodException {
        Method get = KioscoConfigController.class.getMethod("configuracion");
        Method rotar = KioscoConfigController.class.getMethod("rotar");

        assertThat(get.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
        assertThat(rotar.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
    }
}