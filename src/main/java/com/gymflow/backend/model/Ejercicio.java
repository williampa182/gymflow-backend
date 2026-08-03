package com.gymflow.backend.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Ejercicio de una rutina (Fase 4). Pertenece a una sola rutina y se
 * elimina con ella (orphanRemoval en Rutina.ejercicios + ON DELETE CASCADE
 * en la migración 003).
 */
@Entity
@Table(name = "ejercicios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ejercicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rutina_id", nullable = false)
    private Rutina rutina;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false)
    private Integer series;

    @Column(nullable = false)
    private Integer repeticiones;

    @Column(nullable = false)
    private Integer orden;
}
