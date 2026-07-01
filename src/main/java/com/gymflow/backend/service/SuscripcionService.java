package com.gymflow.backend.service;

import com.gymflow.backend.dto.SuscripcionRequestDTO;
import com.gymflow.backend.dto.SuscripcionResponseDTO;
import com.gymflow.backend.model.Plan;
import com.gymflow.backend.model.Suscripcion;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.EstadoSuscripcion;
import com.gymflow.backend.repository.PlanRepository;
import com.gymflow.backend.repository.SuscripcionRepository;
import com.gymflow.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SuscripcionService {

    private final SuscripcionRepository suscripcionRepository;
    private final UsuarioRepository usuarioRepository;
    private final PlanRepository planRepository;

    @SuppressWarnings("null")
    public SuscripcionResponseDTO crear(SuscripcionRequestDTO request) {
        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con id: " + request.getUsuarioId()));

        Plan plan = planRepository.findById(request.getPlanId())
                .orElseThrow(() -> new RuntimeException("Plan no encontrado con id: " + request.getPlanId()));

        suscripcionRepository.findByUsuarioIdAndEstado(usuario.getId(), EstadoSuscripcion.ACTIVA)
                .ifPresent(s -> { throw new RuntimeException("El usuario ya tiene una suscripción activa"); });

        Suscripcion suscripcion = Suscripcion.builder()
                .usuario(usuario)
                .plan(plan)
                .fechaInicio(request.getFechaInicio())
                .fechaFin(request.getFechaInicio().plusDays(plan.getDuracionDias()))
                .estado(EstadoSuscripcion.ACTIVA)
                .build();

        suscripcionRepository.save(suscripcion);
        return toDTO(suscripcion);
    }

    public List<SuscripcionResponseDTO> listarPorUsuario(Long usuarioId) {
        return suscripcionRepository.findByUsuarioId(usuarioId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public List<SuscripcionResponseDTO> listarPorEstado(EstadoSuscripcion estado) {
        List<Suscripcion> suscripciones = (estado != null)
                ? suscripcionRepository.findByEstado(estado)
                : suscripcionRepository.findAll();

        return suscripciones.stream()
                .map(this::toDTO)
                .toList();
    }

    @SuppressWarnings("null")
    public SuscripcionResponseDTO cancelar(Long id) {
        Suscripcion suscripcion = suscripcionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Suscripción no encontrada con id: " + id));

        if (suscripcion.getEstado() != EstadoSuscripcion.ACTIVA) {
            throw new RuntimeException("Solo se pueden cancelar suscripciones activas");
        }

        suscripcion.setEstado(EstadoSuscripcion.CANCELADA);
        suscripcionRepository.save(suscripcion);
        return toDTO(suscripcion);
    }

    private SuscripcionResponseDTO toDTO(Suscripcion s) {
        return SuscripcionResponseDTO.builder()
                .id(s.getId())
                .usuarioId(s.getUsuario().getId())
                .nombreUsuario(s.getUsuario().getNombre())
                .planId(s.getPlan().getId())
                .nombrePlan(s.getPlan().getNombre())
                .fechaInicio(s.getFechaInicio())
                .fechaFin(s.getFechaFin())
                .estado(s.getEstado())
                .creadoEn(s.getCreadoEn())
                .build();
    }
}