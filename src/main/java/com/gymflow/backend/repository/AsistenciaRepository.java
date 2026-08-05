package com.gymflow.backend.repository;

import com.gymflow.backend.model.Asistencia;
import com.gymflow.backend.repository.projection.AsistenciaPorFechaProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AsistenciaRepository extends JpaRepository<Asistencia, Long> {

    boolean existsByUsuarioIdAndFecha(Long usuarioId, LocalDate fecha);

    List<Asistencia> findByUsuarioIdAndFechaBetween(Long usuarioId, LocalDate desde, LocalDate hasta);

    // Fase 5, P5: semana del ENTRENADOR — batch por clienteIds (sin N+1,
    // patrón Fase 4) + historial paginado del ADMIN + stats agrupadas.
    List<Asistencia> findByUsuarioIdInAndFechaBetween(
            Collection<Long> usuarioIds, LocalDate desde, LocalDate hasta);

    Page<Asistencia> findByUsuarioId(Long usuarioId, Pageable pageable);

    @Query("""
            select a.fecha as fecha, count(a) as cantidad
            from Asistencia a
            where a.fecha between :desde and :hasta
            group by a.fecha
            """)
    List<AsistenciaPorFechaProjection> contarPorFecha(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);
}
