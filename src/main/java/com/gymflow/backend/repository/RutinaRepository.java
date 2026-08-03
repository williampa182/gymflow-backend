package com.gymflow.backend.repository;

import com.gymflow.backend.model.Rutina;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RutinaRepository extends JpaRepository<Rutina, Long> {

    List<Rutina> findByEntrenadorIdOrderByCreadoEnDesc(Long entrenadorId);

    Optional<Rutina> findByIdAndEntrenadorId(Long id, Long entrenadorId);

    /**
     * Rutinas ACTIVAS asignadas al cliente, más recientes primero. El
     * filtro de activo en la query es defensa en profundidad: el frontend
     * solo debería mostrar rutinas que el entrenador no haya desactivado.
     */
    @Query("""
            select ar.rutina
            from AsignacionRutina ar
            where ar.cliente.id = :clienteId
              and ar.rutina.activo = true
            order by ar.asignadoEn desc
            """)
    List<Rutina> findRutinasActivasAsignadas(@Param("clienteId") Long clienteId);
}
