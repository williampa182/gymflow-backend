package com.gymflow.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Rutina de entrenamiento creada por un ENTRENADOR (Fase 4). El acceso de
 * escritura es del creador (ownership verificado en RutinaService); los
 * CLIENTES con acompañamiento activo la reciben vía AsignacionRutina.
 */
@Entity
@Table(name = "rutinas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rutina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entrenador_id", nullable = false)
    private Usuario entrenador;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @Builder.Default
    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    // Optimistic locking: protege contra ediciones concurrentes de la MISMA
    // rutina (dos requests modificando la rutina a la vez). GlobalExceptionHandler
    // traduce OptimisticLockingFailureException a 409.
    @Version
    private Long version;

    @Builder.Default
    @OneToMany(mappedBy = "rutina", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    private List<Ejercicio> ejercicios = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        creadoEn = LocalDateTime.now();
    }

    // Utilidad para reemplazar el set de ejercicios en el PUT (evita que el
    // cliente mande ejercicios sueltos sin rutina: siempre se re-enlazan).
    public void reemplazarEjercicios(List<Ejercicio> nuevos) {
        ejercicios.clear();
        nuevos.forEach(ejercicio -> ejercicio.setRutina(this));
        ejercicios.addAll(nuevos);
    }
}
