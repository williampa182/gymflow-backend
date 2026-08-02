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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SuscripcionService {

    private final SuscripcionRepository suscripcionRepository;
    private final UsuarioRepository usuarioRepository;
    private final PlanRepository planRepository;

    @SuppressWarnings("null")
    @Transactional
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

        try {
            // El chequeo de arriba (findByUsuarioIdAndEstado) sigue teniendo
            // una ventana de carrera bajo concurrencia real (check-then-act
            // clásico): dos requests pueden pasar el chequeo antes de que
            // cualquiera de las dos haga commit. Este try/catch es la red de
            // seguridad para cuando exista la constraint única parcial a
            // nivel de Postgres (ver scripts/migrations/001_unique_suscripcion_activa.sql
            // — TODAVÍA NO APLICADA, hay que correrla a mano). Sin esa
            // constraint, este catch no dispara y la carrera sigue abierta.
            suscripcionRepository.save(suscripcion);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("El usuario ya tiene una suscripción activa");
        }
        return toDTO(suscripcion);
    }

    public Page<SuscripcionResponseDTO> listarPorUsuario(Long usuarioId, Pageable pageable) {
        return suscripcionRepository.findByUsuarioId(usuarioId, pageable)
                .map(this::toDTO);
    }

    public Page<SuscripcionResponseDTO> listarPorUsuarioEmail(String email, Pageable pageable) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con email: " + email));

        return suscripcionRepository.findByUsuarioId(usuario.getId(), pageable)
                .map(this::toDTO);
    }

    public Page<SuscripcionResponseDTO> listarPorEstado(EstadoSuscripcion estado, Pageable pageable) {
        Page<Suscripcion> suscripciones = (estado != null)
                ? suscripcionRepository.findByEstado(estado, pageable)
                : suscripcionRepository.findAll(pageable);

        return suscripciones.map(this::toDTO);
    }

    @SuppressWarnings("null")
    @Transactional
    public SuscripcionResponseDTO cancelar(Long id) {
        Suscripcion suscripcion = suscripcionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Suscripción no encontrada con id: " + id));

        if (suscripcion.getEstado() != EstadoSuscripcion.ACTIVA) {
            throw new RuntimeException("Solo se pueden cancelar suscripciones activas");
        }

        suscripcion.setEstado(EstadoSuscripcion.CANCELADA);
        // El @Version en Suscripcion hace que este save() falle con
        // OptimisticLockingFailureException si otra transacción modificó la
        // misma fila entre el findById de arriba y este save (ej. dos
        // cancelaciones simultáneas de la misma suscripción). El
        // GlobalExceptionHandler ya traduce eso a un 409 claro.
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
