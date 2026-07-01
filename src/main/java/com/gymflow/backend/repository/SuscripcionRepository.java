package com.gymflow.backend.repository;

import com.gymflow.backend.model.Suscripcion;
import com.gymflow.backend.model.enums.EstadoSuscripcion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {
    List<Suscripcion> findByUsuarioId(Long usuarioId);
    List<Suscripcion> findByEstado(EstadoSuscripcion estado);
    Optional<Suscripcion> findByUsuarioIdAndEstado(Long usuarioId, EstadoSuscripcion estado);
}