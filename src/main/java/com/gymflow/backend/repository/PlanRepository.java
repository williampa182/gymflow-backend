package com.gymflow.backend.repository;

import com.gymflow.backend.model.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanRepository extends JpaRepository<Plan, Long> {
    List<Plan> findByActivo(boolean activo);
    boolean existsByNombre(String nombre);
}