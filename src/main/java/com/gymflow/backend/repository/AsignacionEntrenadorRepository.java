package com.gymflow.backend.repository;

import com.gymflow.backend.model.AsignacionEntrenador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AsignacionEntrenadorRepository extends JpaRepository<AsignacionEntrenador, Long> {

    Optional<AsignacionEntrenador> findByClienteIdAndActivaTrue(Long clienteId);

    List<AsignacionEntrenador> findByEntrenadorIdAndActivaTrueOrderByAsignadoEnDesc(Long entrenadorId);

    /**
     * Para cancelar: solo el entrenador que creó la asignación puede
     * cancelarla (ownership verificado en EntrenadorService).
     */
    Optional<AsignacionEntrenador> findByIdAndEntrenadorId(Long id, Long entrenadorId);
}
