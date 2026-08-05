package com.gymflow.backend.model;

import com.gymflow.backend.model.enums.MetodoAsistencia;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Asistencia / check-in del gimnasio (Fase 5). Regla de negocio central:
 * como mucho UNA entrada por día por usuario.
 *
 * La constraint única (usuario_id, fecha) es la red de seguridad a nivel BD
 * del chequeo check-then-act de AsistenciaService (1/día) — mismo patrón que
 * el índice único parcial de suscripciones ACTIVA (migración 001). Se
 * declara con nombre EXPLÍCITO porque DEBE coincidir con el índice
 * uq_asistencia_por_dia de la migración 005:
 *  - En dev/test el esquema lo crea Hibernate (ddl-auto: update) y sin esta
 *    declaración el duplicado del día no generaría DataIntegrityViolation
 *    real (los scripts SQL no corren contra la BD de test).
 *  - En prod el nombre coincide con el CREATE UNIQUE INDEX de 005 (en
 *    Postgres constraint única e índice comparten namespace/nombre), así que
 *    Hibernate con ddl-auto: validate no ve divergencia.
 */
@Entity
@Table(name = "asistencias",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_asistencia_por_dia",
                columnNames = {"usuario_id", "fecha"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asistencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "entrada_en")
    private LocalDateTime entradaEn;

    // Reservado para el check-out / "dentro del gimnasio" (backlog
    // post-MVP); hoy queda NULL siempre.
    @Column(name = "salida_en")
    private LocalDateTime salidaEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo")
    private MetodoAsistencia metodo;

    // Sin @PrePersist: entradaEn lo setea SIEMPRE el service con el Clock de
    // Bogotá (regla 7). Un @PrePersist con LocalDateTime.now() usaría la TZ
    // del servidor (UTC en Railway) y un check-in a las 23:30 Bogotá quedaría
    // con entradaEn de "mañana". Invariante: solo AsistenciaService (SELF),
    // el kiosco (KIOSK_CARNET, P4) y el control de acceso (ADMIN, P5) crean
    // asistencias.
}