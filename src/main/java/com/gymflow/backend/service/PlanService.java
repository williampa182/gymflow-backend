package com.gymflow.backend.service;

import com.gymflow.backend.dto.PlanRequestDTO;
import com.gymflow.backend.dto.PlanResponseDTO;
import com.gymflow.backend.model.Plan;
import com.gymflow.backend.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanRepository planRepository;

    @SuppressWarnings("null")
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

    // Se saca @Cacheable de este método (propuesta de Codex, hallazgo 3.3):
    // cachear resultados paginados con distintos filtros/orden/tamaño de
    // página tendría cardinalidad alta y poco valor real de cache. Si se
    // quiere volver a cachear, hace falta una política específica para eso.
    public Page<PlanResponseDTO> listar(Boolean activo, Pageable pageable) {
        Page<Plan> planes = (activo != null)
                ? planRepository.findByActivo(activo, pageable)
                : planRepository.findAll(pageable);

        return planes.map(this::toDTO);
    }

    @SuppressWarnings("null")
    public PlanResponseDTO obtenerPorId(Long id) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Plan no encontrado con id: " + id));
        return toDTO(plan);
    }

    @SuppressWarnings("null")
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