package com.gymflow.backend.model;

import com.gymflow.backend.model.enums.EstadoSuscripcion;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "suscripciones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Suscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoSuscripcion estado;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    // Tracking del aviso de vencimiento (ver scripts/migrations/
    // 002_notificado_en_suscripciones.sql): se setea cuando se notifica el
    // vencimiento al usuario y deja de ser null, para que el job diario no
    // reenvíe el email a la misma suscripción.
    @Column(name = "notificado_en")
    private LocalDateTime notificadoEn;

    // Optimistic locking: protege contra el caso "dos requests modifican la
    // MISMA suscripción a la vez" (ej. dos admins cancelando/editando el
    // mismo registro en simultáneo). Hibernate incrementa esta columna en
    // cada UPDATE y falla con OptimisticLockingFailureException si otra
    // transacción ya la modificó entre el read y el write — GlobalExceptionHandler
    // ya traduce eso a un 409 claro.
    //
    // OJO — esto NO cubre el otro caso de la carrera (dos requests creando
    // DOS suscripciones activas DISTINTAS para el mismo usuario al mismo
    // tiempo). Ese caso es un problema de integridad de negocio, no de
    // update concurrente sobre una fila existente, y requiere una
    // constraint a nivel de base de datos (ver
    // scripts/migrations/001_unique_suscripcion_activa.sql).
    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        creadoEn = LocalDateTime.now();
        if (fechaInicio != null && plan != null) {
            fechaFin = fechaInicio.plusDays(plan.getDuracionDias());
        }
    }
}