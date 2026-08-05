package com.gymflow.backend.repository;

import com.gymflow.backend.model.KioscoConfig;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de la fila única de configuración del kiosco (Fase 5). La app
 * solo trabaja con el id fijo {@link KioscoConfig#FILA_UNICA_ID} (1).
 */
public interface KioscoConfigRepository extends JpaRepository<KioscoConfig, Long> {
}
