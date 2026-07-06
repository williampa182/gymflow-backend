package com.gymflow.backend.service;

import com.gymflow.backend.dto.PlanRequestDTO;
import com.gymflow.backend.dto.PlanResponseDTO;
import com.gymflow.backend.model.Plan;
import com.gymflow.backend.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanRepository planRepository;

    @SuppressWarnings("null")
    @CacheEvict(value = "planes", allEntries = true)
    public PlanResponseDTO crear(PlanRequestDTO request) {
        if (planRepository.existsByNombre(request.getNombre())) {
            throw new RuntimeException("Ya existe un plan con ese nombre");
        }

        Plan plan = Plan.builder()
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .precio(request.getPrecio())
                .duracionDias(request.getDuracionDias())
                .tipo(request.getTipo())
                .limiteClases(request.getLimiteClases())
                .incluyeClases(request.isIncluyeClases())
                .incluyeEntrenadorPersonal(request.isIncluyeEntrenadorPersonal())
                .build();

        planRepository.save(plan);
        return toDTO(plan);
    }

    @Cacheable(value = "planes", key = "#activo != null ? #activo : 'todos'")
    public List<PlanResponseDTO> listar(Boolean activo) {
        List<Plan> planes = (activo != null)
                ? planRepository.findByActivo(activo)
                : planRepository.findAll();

        return planes.stream()
                .map(this::toDTO)
                .toList();
    }

    @SuppressWarnings("null")
    public PlanResponseDTO obtenerPorId(Long id) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Plan no encontrado con id: " + id));
        return toDTO(plan);
    }

    @SuppressWarnings("null")
    @CacheEvict(value = "planes", allEntries = true)
    public PlanResponseDTO actualizar(Long id, PlanRequestDTO request) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Plan no encontrado con id: " + id));

        plan.setNombre(request.getNombre());
        plan.setDescripcion(request.getDescripcion());
        plan.setPrecio(request.getPrecio());
        plan.setDuracionDias(request.getDuracionDias());
        plan.setTipo(request.getTipo());
        plan.setLimiteClases(request.getLimiteClases());
        plan.setIncluyeClases(request.isIncluyeClases());
        plan.setIncluyeEntrenadorPersonal(request.isIncluyeEntrenadorPersonal());

        planRepository.save(plan);
        return toDTO(plan);
    }

    @SuppressWarnings("null")
    @CacheEvict(value = "planes", allEntries = true)
    public PlanResponseDTO cambiarEstado(Long id, boolean activo) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Plan no encontrado con id: " + id));

        plan.setActivo(activo);
        planRepository.save(plan);
        return toDTO(plan);
    }

    private PlanResponseDTO toDTO(Plan p) {
        return PlanResponseDTO.builder()
                .id(p.getId())
                .nombre(p.getNombre())
                .descripcion(p.getDescripcion())
                .precio(p.getPrecio())
                .duracionDias(p.getDuracionDias())
                .tipo(p.getTipo())
                .limiteClases(p.getLimiteClases())
                .incluyeClases(p.isIncluyeClases())
                .incluyeEntrenadorPersonal(p.isIncluyeEntrenadorPersonal())
                .activo(p.isActivo())
                .creadoEn(p.getCreadoEn())
                .build();
    }
}