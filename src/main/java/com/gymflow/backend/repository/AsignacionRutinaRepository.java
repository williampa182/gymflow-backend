package com.gymflow.backend.repository;

import com.gymflow.backend.model.AsignacionRutina;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AsignacionRutinaRepository extends JpaRepository<AsignacionRutina, Long> {

    boolean existsByClienteIdAndRutinaId(Long clienteId, Long rutinaId);

    Optional<AsignacionRutina> findByClienteIdAndRutinaId(Long clienteId, Long rutinaId);
}
