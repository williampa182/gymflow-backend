package com.gymflow.backend.repository;

import com.gymflow.backend.model.AsignacionRutina;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AsignacionRutinaRepository extends JpaRepository<AsignacionRutina, Long> {

    boolean existsByClienteIdAndRutinaId(Long clienteId, Long rutinaId);

    Optional<AsignacionRutina> findByClienteIdAndRutinaId(Long clienteId, Long rutinaId);

    /**
     * Todas las asignaciones de las rutinas de un entrenador, con el
     * cliente resuelto (join fetch) para no disparar N+1 al leer los
     * nombres en listarMias de RutinaService.
     */
    @Query("""
            select ar
            from AsignacionRutina ar
            join fetch ar.cliente
            where ar.rutina.entrenador.id = :entrenadorId
            """)
    List<AsignacionRutina> findByRutinaEntrenadorId(@Param("entrenadorId") Long entrenadorId);
}
