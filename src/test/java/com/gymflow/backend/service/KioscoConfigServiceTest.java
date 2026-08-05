package com.gymflow.backend.service;

import com.gymflow.backend.model.KioscoConfig;
import com.gymflow.backend.repository.KioscoConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KioscoConfigService (Fase 5, P4): la key del dispositivo vive SOLO como
 * BCrypt en la fila única; nunca en logs ni en la API. La siembra desde
 * KIOSK_API_KEY es aditiva (no sobrescribe lo ya configurado); rotar()
 * devuelve la clave nueva en texto plano UNA vez e invalida la anterior.
 */
@ExtendWith(MockitoExtension.class)
class KioscoConfigServiceTest {

    @Mock
    private KioscoConfigRepository kioscoConfigRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private KioscoConfigService servicio;

    @BeforeEach
    void setUp() {
        servicio = new KioscoConfigService(kioscoConfigRepository, encoder);
    }

    private void conApiKeyDelEntorno(String clave) {
        ReflectionTestUtils.setField(servicio, "apiKeyDelEntorno", clave);
    }

    private KioscoConfig filaSinClave() {
        return KioscoConfig.builder().id(KioscoConfig.FILA_UNICA_ID).build();
    }

    @Test
    void sembrarSinApiKeyDelEntornoNoConfiguraNada() {
        conApiKeyDelEntorno("");
        when(kioscoConfigRepository.findById(KioscoConfig.FILA_UNICA_ID)).thenReturn(Optional.empty());

        servicio.sembrarDesdeEntorno();

        verify(kioscoConfigRepository, never()).save(any());
        assertThat(servicio.configurada()).isFalse();
    }

    @Test
    void siembraDesdeElEntornoSoloSiNoHabiaClavePrevia() {
        conApiKeyDelEntorno("clave-de-primera-instalacion");
        when(kioscoConfigRepository.findById(KioscoConfig.FILA_UNICA_ID)).thenReturn(Optional.of(filaSinClave()));
        // save devuelve la fila con el hash ya puesto (flujo real de JPA)
        when(kioscoConfigRepository.save(any(KioscoConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        servicio.sembrarDesdeEntorno();

        verify(kioscoConfigRepository).save(any(KioscoConfig.class));
        assertThat(servicio.configurada()).isTrue();
        // y la clave queda validable con BCrypt
        assertThat(servicio.validar("clave-de-primera-instalacion")).isTrue();
    }

    @Test
    void siembraNoSobrescribeUnaClaveYaConfigurada() {
        // redeploy con otra KIOSK_API_KEY: la rotación es siempre explícita
        String clavePrevia = "clave-que-el-admin-guardo";
        KioscoConfig config = filaSinClave();
        config.setApiKeyHash(encoder.encode(clavePrevia));
        conApiKeyDelEntorno("otra-clave-del-redeploy");
        when(kioscoConfigRepository.findById(KioscoConfig.FILA_UNICA_ID)).thenReturn(Optional.of(config));

        servicio.sembrarDesdeEntorno();

        verify(kioscoConfigRepository, never()).save(any());
        assertThat(servicio.validar(clavePrevia)).isTrue();
        assertThat(servicio.validar("otra-clave-del-redeploy")).isFalse();
    }

    @Test
    void validarRechazaSinFilaSinHashYConClaveNulaOVacia() {
        // fail-closed: cualquier camino sin credencial válida → false
        when(kioscoConfigRepository.findById(KioscoConfig.FILA_UNICA_ID)).thenReturn(Optional.empty());
        assertThat(servicio.validar("cualquiera")).isFalse();

        when(kioscoConfigRepository.findById(KioscoConfig.FILA_UNICA_ID)).thenReturn(Optional.of(filaSinClave()));
        assertThat(servicio.validar("cualquiera")).isFalse();

        KioscoConfig config = filaSinClave();
        config.setApiKeyHash(encoder.encode("la-real"));
        when(kioscoConfigRepository.findById(KioscoConfig.FILA_UNICA_ID)).thenReturn(Optional.of(config));
        assertThat(servicio.validar(null)).isFalse();
        assertThat(servicio.validar("  ")).isFalse();
        assertThat(servicio.validar("la-real")).isTrue();
    }

    @Test
    void rotarDevuelveClaveNuevaQueValidaEInvalidaLaAnterior() {
        String claveAnterior = "clave-anterior";
        KioscoConfig config = filaSinClave();
        config.setApiKeyHash(encoder.encode(claveAnterior));
        when(kioscoConfigRepository.findById(KioscoConfig.FILA_UNICA_ID)).thenReturn(Optional.of(config));
        when(kioscoConfigRepository.save(any(KioscoConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String nueva = servicio.rotar();

        assertThat(nueva).isNotBlank().isNotEqualTo(claveAnterior);
        assertThat(servicio.validar(nueva)).isTrue();
        assertThat(servicio.validar(claveAnterior)).isFalse();
    }

    @Test
    void rotarCreaLaFilaSiNoExiste() {
        // Simulación honesta de la BD: findById devuelve vacío hasta que el
        // save "persiste" la fila (id fijo 1) — luego ya está legible.
        java.util.concurrent.atomic.AtomicReference<KioscoConfig> guardada =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(kioscoConfigRepository.findById(KioscoConfig.FILA_UNICA_ID))
                .thenAnswer(inv -> Optional.ofNullable(guardada.get()));
        when(kioscoConfigRepository.save(any(KioscoConfig.class))).thenAnswer(inv -> {
            guardada.set(inv.getArgument(0));
            return inv.getArgument(0);
        });

        String nueva = servicio.rotar();

        assertThat(nueva).isNotBlank();
        assertThat(guardada.get()).isNotNull();
        assertThat(servicio.validar(nueva)).isTrue();
    }
}
