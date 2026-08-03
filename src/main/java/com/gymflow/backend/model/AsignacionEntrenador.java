package com.gymflow.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Acompañamiento: un ENTRENADOR se asigna como acompañante de un CLIENTE
 * cuyo plan activo incluye entrenador personal (Fase 4). El cliente puede
 * tener como mucho UNA asignación ACTIVA — el índice único parcial
 * uq_acompanante_activo_por_cliente (migración 004) es la red de seguridad
 * a nivel BD para el chequeo check-then-act de EntrenadorService.asignarme().
 */
@Entity
@Table(name = "asignaciones_entrenador")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AsignacionEntrenador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Usuario cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entrenador_id", nullable = false)
    private Usuario entrenador;

    @Builder.Default
    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "asignado_en")
    private LocalDateTime asignadoEn;

    // Optimistic locking: protege contra dos cancelaciones concurrentes de
    // la misma asignación (mismo patrón que Suscripcion.version).
    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        asignadoEn = LocalDateTime.now();
    }
}
