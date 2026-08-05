package com.gymflow.backend.service;

import com.gymflow.backend.model.KioscoConfig;
import com.gymflow.backend.repository.KioscoConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * ApiKey de la configuración del kiosco de recepción (Fase 5). La clave se
 * guarda SOLO como hash BCrypt en la fila única de kiosco_config; en texto
 * plano existe únicamente en KIOSK_API_KEY del entorno (siembra en el primer
 * boot) o en la respuesta única de rotar(). Nunca en logs ni en lecturas.
 *
 * Fail-closed de configuración: sin clave sembrada, configurada() = false y
 * validar() rechaza todo → el kiosco degrada a "Iniciar sesión con cuenta
 * ADMIN". Nada de esto revela la clave.
 */
@Service
@RequiredArgsConstructor
public class KioscoConfigService {

    private static final Logger log = LoggerFactory.getLogger(KioscoConfigService.class);

    private final KioscoConfigRepository kioscoConfigRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.kiosk.api-key:}")
    private String apiKeyDelEntorno;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Siembra desde el entorno al boot (aditiva e idempotente): si ya existe
     * una clave configurada se conserva — la rotación es explícita vía
     * ADMIN, nunca implícita por un redeploy con otro KIOSK_API_KEY.
     */
    @PostConstruct
    @Transactional
    public void sembrarDesdeEntorno() {
        String clave = apiKeyDelEntorno == null ? "" : apiKeyDelEntorno.trim();
        if (clave.isEmpty()) {
            log.info("Kiosco sin KIOSK_API_KEY: no configurado (fail-closed, degrada a cuenta ADMIN)");
            return;
        }
        KioscoConfig config = obtenerConfig();
        if (config.getApiKeyHash() != null && !config.getApiKeyHash().isBlank()) {
            log.info("Kiosco ya configurado: se conserva la clave actual (no se re-sembra desde el entorno)");
            return;
        }
        config.setApiKeyHash(passwordEncoder.encode(clave));
        kioscoConfigRepository.save(config);
        log.info("Kiosco configurado desde KIOSK_API_KEY (solo BCrypt en BD, nunca la clave en logs)");
    }

    @Transactional(readOnly = true)
    public boolean configurada() {
        KioscoConfig config = kioscoConfigRepository.findById(KioscoConfig.FILA_UNICA_ID).orElse(null);
        return config != null && config.getApiKeyHash() != null && !config.getApiKeyHash().isBlank();
    }

    /** Validación de la credencial del dispositivo (BCrypt). Nunca loguea la clave. */
    @Transactional(readOnly = true)
    public boolean validar(String clave) {
        KioscoConfig config = kioscoConfigRepository.findById(KioscoConfig.FILA_UNICA_ID).orElse(null);
        if (config == null || config.getApiKeyHash() == null || clave == null || clave.isBlank()) {
            return false;
        }
        return passwordEncoder.matches(clave, config.getApiKeyHash());
    }

    /**
     * Rotación (POST /api/kiosco/config/rotar, ADMIN). Devuelve la clave nueva
     * en texto plano UNA sola vez; la anterior deja de ser válida de inmediato.
     */
    @Transactional
    public String rotar() {
        String nuevaClave = generarClave();
        KioscoConfig config = obtenerConfig();
        config.setApiKeyHash(passwordEncoder.encode(nuevaClave));
        kioscoConfigRepository.save(config);
        return nuevaClave;
    }

    private KioscoConfig obtenerConfig() {
        return kioscoConfigRepository.findById(KioscoConfig.FILA_UNICA_ID)
                .orElseGet(() -> KioscoConfig.builder().id(KioscoConfig.FILA_UNICA_ID).build());
    }

    /**
     * 32 bytes aleatorios en hex (64 chars) con SecureRandom — suficiente para
     * la credencial de un terminal de recepción (rotable desde la UI).
     */
    private String generarClave() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}