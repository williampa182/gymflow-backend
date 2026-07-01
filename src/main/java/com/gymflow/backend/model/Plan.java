package com.gymflow.backend.model;

import com.gymflow.backend.model.enums.TipoPlan;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "planes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(nullable = false)
    private Integer duracionDias;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoPlan tipo;

    @Column
    private Integer limiteClases;

    @Builder.Default
    @Column(nullable = false)
    private boolean incluyeClases = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean incluyeEntrenadorPersonal = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @PrePersist
    protected void onCreate() {
        creadoEn = LocalDateTime.now();
    }
}