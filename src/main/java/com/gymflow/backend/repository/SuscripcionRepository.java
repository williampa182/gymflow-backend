package com.gymflow.backend.repository;

import com.gymflow.backend.model.Suscripcion;
import com.gymflow.backend.model.enums.EstadoSuscripcion;
import com.gymflow.backend.repository.projection.IngresoPorTipoPlanProjection;
import com.gymflow.backend.repository.projection.SuscripcionPorEstadoProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {
    List<Suscripcion> findByUsuarioId(Long usuarioId);
    Page<Suscripcion> findByUsuarioId(Long usuarioId, Pageable pageable);
    List<Suscripcion> findByEstado(EstadoSuscripcion estado);
    Page<Suscripcion> findByEstado(EstadoSuscripcion estado, Pageable pageable);
    Optional<Suscripcion> findByUsuarioIdAndEstado(Long usuarioId, EstadoSuscripcion estado);

    /**
     * Suscripciones ACTIVAS de un conjunto de usuarios en una sola query
     * (aplanar el N+1 de EntrenadorService.listarClientesElegibles). El
     * índice único uq_suscripcion_activa_por_usuario garantiza a lo sumo
     * una por usuario.
     */
    List<Suscripcion> findByEstadoAndUsuarioIdIn(EstadoSuscripcion estado, Collection<Long> usuarioIds);

    @Query("""
            select s.estado as estado, count(s) as cantidad
            from Suscripcion s
            group by s.estado
            """)
    List<SuscripcionPorEstadoProjection> contarSuscripcionesPorEstado();

    @Query("""
            select p.tipo as tipoPlan,
                   coalesce(sum(p.precio), 0) as ingresoEstimado,
                   count(s) as cantidadSuscripciones
            from Suscripcion s
            join s.plan p
            where s.estado = :estado
            group by p.tipo
            """)
    List<IngresoPorTipoPlanProjection> ingresosEstimadosPorTipoPlan(
            @Param("estado") EstadoSuscripcion estado);

    @Query("""
            select s
            from Suscripcion s
            join s.usuario u
            where s.estado = :estado
              and s.fechaFin between :desde and :hasta
              and s.notificadoEn is null
              and u.activo = true
            """)
    List<Suscripcion> findPendientesAvisoVencimiento(
            @Param("estado") EstadoSuscripcion estado,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);
}
