package com.gymflow.backend.service;

import com.gymflow.backend.dto.EnRiesgoDTO;
import com.gymflow.backend.dto.SuscripcionRequestDTO;
import com.gymflow.backend.dto.ConteoSuscripcionesDTO;
import com.gymflow.backend.dto.SuscripcionResponseDTO;
import com.gymflow.backend.model.Asistencia;
import com.gymflow.backend.model.Plan;
import com.gymflow.backend.model.Suscripcion;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.EstadoSuscripcion;
import com.gymflow.backend.model.enums.TipoPlan;
import com.gymflow.backend.repository.AsistenciaRepository;
import com.gymflow.backend.repository.PlanRepository;
import com.gymflow.backend.repository.AsistenciaRepository;
import com.gymflow.backend.repository.SuscripcionRepository;
import com.gymflow.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SuscripcionService {

    private final SuscripcionRepository suscripcionRepository;
    private final UsuarioRepository usuarioRepository;
    private final PlanRepository planRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final Clock clock;

    @SuppressWarnings("null")
    @Transactional
    public SuscripcionResponseDTO crear(SuscripcionRequestDTO request) {
        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con id: " + request.getUsuarioId()));

        Plan plan = planRepository.findById(request.getPlanId())
                .orElseThrow(() -> new RuntimeException("Plan no encontrado con id: " + request.getPlanId()));

        // D1-A: solo una suscripción VIGENTE bloquea. Si la existente está
        // vencida de facto (ACTIVA con fecha pasada), se transiciona a
        // VENCIDA en esta misma transacción y la nueva nace ACTIVA
        // (renovación sin cancelación manual).
        // Pase diario ("solo hoy": fechaFin = inicio): el de ayer ya no es
        // vigente hoy, así que la recompra del día siguiente renueva por
        // este mismo camino sin 409. El mismo día sí bloquea con 409
        // (ya tiene su pase de hoy).
        LocalDate hoy = LocalDate.now(clock);
        suscripcionRepository.findByUsuarioIdAndEstado(usuario.getId(), EstadoSuscripcion.ACTIVA)
                .ifPresent(vieja -> {
                    if (VigenciaSuscripcion.vigente(vieja, hoy)) {
                        throw new RuntimeException("El usuario ya tiene una suscripción activa");
                    }
                    vieja.setEstado(EstadoSuscripcion.VENCIDA);
                    // saveAndFlush a propósito (no save): Hibernate vacía
                    // INSERTs antes que UPDATEs; sin flush explícito el
                    // INSERT de la nueva choca con la vieja aún ACTIVA en
                    // el índice parcial 001 (23505) aunque el UPDATE venga
                    // antes en el código.
                    suscripcionRepository.saveAndFlush(vieja);
                });
        // Congelada bloquea: primero se descongela (la descongelación
        // extiende la fecha), no se pisa con una nueva encima.
        suscripcionRepository.findByUsuarioIdAndEstado(usuario.getId(), EstadoSuscripcion.CONGELADA)
                .ifPresent(congelada -> {
                    throw new RuntimeException(
                            "El usuario ya tiene una suscripción congelada: descongélala antes de crear una nueva");
                });

        Suscripcion suscripcion = Suscripcion.builder()
                .usuario(usuario)
                .plan(plan)
                .fechaInicio(request.getFechaInicio())
                .fechaFin(calcularFechaFin(request.getFechaInicio(), plan))
                .estado(EstadoSuscripcion.ACTIVA)
                .build();

        try {
            // El chequeo de arriba (findByUsuarioIdAndEstado) sigue teniendo
            // una ventana de carrera bajo concurrencia real (check-then-act
            // clásico): dos requests pueden pasar el chequeo antes de que
            // cualquiera de las dos haga commit. Este try/catch es la red de
            // seguridad con la constraint única parcial a nivel de Postgres
            // (scripts/migrations/001_unique_suscripcion_activa.sql).
            // En renovación concurrente, el UPDATE de la vieja choca antes
            // por @Version (OptimisticLockingFailureException → 409).
            suscripcionRepository.save(suscripcion);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("El usuario ya tiene una suscripción activa");
        }
        return toDTO(suscripcion);
    }

    /**
     * Self-service (Fase 3, POST /suscripciones/mi). A diferencia de
     * {@link #crear(SuscripcionRequestDTO)}, la identidad del usuario NUNCA
     * viene del body — llega el email del JWT resuelto en el controller.
     * El pago es mock (demo de portafolio, sin pasarela): el POST simula la
     * aprobación y activa la suscripción directo.
     */
    @SuppressWarnings("null")
    @Transactional
    public SuscripcionResponseDTO inscribir(String email, Long planId, LocalDate fechaInicio) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con email: " + email));

        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan no encontrado con id: " + planId));

        // Defensa en profundidad: el frontend solo lista planes activos, pero
        // acá se re-verifica — un plan inactivo no se puede autocomprar si el
        // catálogo cambió entre el render y el POST (GlobalExceptionHandler
        // mapea este mensaje a 409).
        if (!plan.isActivo()) {
            throw new RuntimeException("El plan no está disponible para inscribirse");
        }

        // D1-A: igual que crear(): solo una VIGENTE bloquea; vencida de
        // facto se transiciona a VENCIDA en esta transacción (renovación).
        LocalDate hoy = LocalDate.now(clock);
        suscripcionRepository.findByUsuarioIdAndEstado(usuario.getId(), EstadoSuscripcion.ACTIVA)
                .ifPresent(vieja -> {
                    if (VigenciaSuscripcion.vigente(vieja, hoy)) {
                        throw new RuntimeException("El usuario ya tiene una suscripción activa");
                    }
                    vieja.setEstado(EstadoSuscripcion.VENCIDA);
                    // saveAndFlush: ver comentario en crear() (orden de flush
                    // INSERT-antes-que-UPDATE vs índice parcial 001).
                    suscripcionRepository.saveAndFlush(vieja);
                });
        suscripcionRepository.findByUsuarioIdAndEstado(usuario.getId(), EstadoSuscripcion.CONGELADA)
                .ifPresent(congelada -> {
                    throw new RuntimeException(
                            "El usuario ya tiene una suscripción congelada: descongélala antes de crear una nueva");
                });

        // "Hoy" sale del Clock de Bogotá (RelojBogotaConfig), NO de
        // LocalDate.now() con la TZ del servidor: Railway corre UTC y un
        // cliente que paga a las 23:30 Bogotá (04:30 UTC del día siguiente)
        // nacería con fechaInicio de "mañana" sin este fix — no podría
        // marcar el mismo día (Fase 5, regla 7).
        LocalDate inicio = fechaInicio != null ? fechaInicio : LocalDate.now(clock);

        Suscripcion suscripcion = Suscripcion.builder()
                .usuario(usuario)
                .plan(plan)
                .fechaInicio(inicio)
                .fechaFin(calcularFechaFin(inicio, plan))
                .estado(EstadoSuscripcion.ACTIVA)
                .build();

        try {
            // Misma red de seguridad check-then-act que en crear(): la
            // constraint única parcial 001_unique_suscripcion_activa.sql
            // cierra la carrera a nivel BD.
            suscripcionRepository.save(suscripcion);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("El usuario ya tiene una suscripción activa");
        }
        return toDTO(suscripcion);
    }

    // readOnly explícito: toDTO navega LAZY (usuario/plan) y fuera de una
    // sesión (llamadas directas en tests, sin OpenSessionInView) eso es
    // LazyInitializationException. Mismo patrón que AsistenciaService.
    @Transactional(readOnly = true)
    public Page<SuscripcionResponseDTO> listarPorUsuario(Long usuarioId, Pageable pageable) {
        return suscripcionRepository.findByUsuarioId(usuarioId, pageable)
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<SuscripcionResponseDTO> listarPorUsuarioEmail(String email, Pageable pageable) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con email: " + email));

        return suscripcionRepository.findByUsuarioId(usuario.getId(), pageable)
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<SuscripcionResponseDTO> listarPorEstado(EstadoSuscripcion estado, Pageable pageable) {
        // D1-A: ACTIVA/VENCIDA son derivadas por fecha (VigenciaSuscripcion),
        // no el estado almacenado tal cual.
        LocalDate hoy = LocalDate.now(clock);
        Page<Suscripcion> suscripciones;
        if (estado == null) {
            suscripciones = suscripcionRepository.findAll(pageable);
        } else if (estado == EstadoSuscripcion.ACTIVA) {
            suscripciones = suscripcionRepository
                    .findByEstadoAndFechaFinGreaterThanEqual(EstadoSuscripcion.ACTIVA, hoy, pageable);
        } else if (estado == EstadoSuscripcion.VENCIDA) {
            suscripciones = suscripcionRepository.findMorosas(
                    EstadoSuscripcion.ACTIVA, EstadoSuscripcion.VENCIDA, hoy, pageable);
        } else {
            suscripciones = suscripcionRepository.findByEstado(estado, pageable);
        }

        return suscripciones.map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public ConteoSuscripcionesDTO contarPorEstado() {
        LocalDate hoy = LocalDate.now(clock);
        return new ConteoSuscripcionesDTO(
                suscripcionRepository.countByEstadoAndFechaFinGreaterThanEqual(
                        EstadoSuscripcion.ACTIVA, hoy),
                suscripcionRepository.countMorosas(
                        EstadoSuscripcion.ACTIVA, EstadoSuscripcion.VENCIDA, hoy),
                suscripcionRepository.countByEstado(EstadoSuscripcion.CANCELADA),
                suscripcionRepository.countByEstado(EstadoSuscripcion.CONGELADA));
    }

    @Transactional(readOnly = true)
    public EnRiesgoDTO sociosEnRiesgo() {
        LocalDate hoy = LocalDate.now(clock);
        List<EnRiesgoDTO.PorVencerDTO> porVencer = suscripcionRepository
                .findByEstadoAndFechaFinBetween(EstadoSuscripcion.ACTIVA, hoy, hoy.plusDays(7))
                .stream()
                // Pases diarios fuera del panel (misma decisión que el email:
                // el visitante paga en caja, no hay qué cobrarle después).
                .filter(s -> s.getPlan() == null
                        || s.getPlan().getTipo() != TipoPlan.PASE_DIARIO)
                .map(s -> new EnRiesgoDTO.PorVencerDTO(
                        s.getUsuario().getId(),
                        s.getUsuario().getNombre(),
                        s.getPlan().getNombre(),
                        s.getFechaFin(),
                        ChronoUnit.DAYS.between(hoy, s.getFechaFin()),
                        s.getUsuario().getTelefono()))
                .sorted(Comparator.comparing(EnRiesgoDTO.PorVencerDTO::fechaFin))
                .toList();

        List<Suscripcion> vigentes = suscripcionRepository
                .findByEstadoAndFechaFinGreaterThanEqual(EstadoSuscripcion.ACTIVA, hoy)
                .stream()
                .filter(s -> s.getPlan() == null
                        || s.getPlan().getTipo() != TipoPlan.PASE_DIARIO)
                .limit(500).toList();
        LocalDate desde = hoy.minusDays(15);
        Map<Long, LocalDate> ultimaPorUsuario = new HashMap<>();
        if (!vigentes.isEmpty() && asistenciaRepository != null) {
            List<Long> ids = vigentes.stream().map(s -> s.getUsuario().getId()).toList();
            for (Asistencia a : asistenciaRepository
                    .findByUsuarioIdInAndFechaBetween(ids, desde, hoy)) {
                ultimaPorUsuario.merge(a.getUsuario().getId(), a.getFecha(),
                        (previa, nueva) -> previa.isAfter(nueva) ? previa : nueva);
            }
        }
        List<EnRiesgoDTO.InactivoDTO> inactivos = vigentes.stream()
                .filter(s -> {
                    LocalDate ultima = ultimaPorUsuario.get(s.getUsuario().getId());
                    return ultima == null || ultima.isBefore(desde);
                })
                .map(s -> {
                    LocalDate ultima = ultimaPorUsuario.get(s.getUsuario().getId());
                    long dias = ultima == null
                            ? ChronoUnit.DAYS.between(s.getFechaInicio(), hoy)
                            : ChronoUnit.DAYS.between(ultima, hoy);
                    return new EnRiesgoDTO.InactivoDTO(
                            s.getUsuario().getId(), s.getUsuario().getNombre(), ultima, dias,
                            s.getUsuario().getTelefono());
                })
                .sorted(Comparator.comparingLong(EnRiesgoDTO.InactivoDTO::diasSinVenir).reversed())
                .toList();
        return new EnRiesgoDTO(porVencer, inactivos);
    }

    // Pase diario = "solo hoy": fechaFin es el mismo día de inicio (NO
    // inicio+duración). El @PrePersist solo rellena cuando fechaFin es null,
    // así que este valor explícito sobrevive al persist.
    private static LocalDate calcularFechaFin(LocalDate inicio, Plan plan) {
        if (plan.getTipo() == TipoPlan.PASE_DIARIO) {
            return inicio;
        }
        return inicio.plusDays(plan.getDuracionDias());
    }

    @SuppressWarnings("null")
    @Transactional
    public SuscripcionResponseDTO cancelar(Long id) {
        Suscripcion suscripcion = suscripcionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Suscripción no encontrada con id: " + id));

        if (suscripcion.getEstado() != EstadoSuscripcion.ACTIVA
                && suscripcion.getEstado() != EstadoSuscripcion.CONGELADA) {
            throw new RuntimeException("Solo se pueden cancelar suscripciones activas o congeladas");
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

    @SuppressWarnings("null")
    @Transactional
    public SuscripcionResponseDTO congelar(Long id) {
        Suscripcion suscripcion = suscripcionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Suscripción no encontrada con id: " + id));

        LocalDate hoy = LocalDate.now(clock);
        if (suscripcion.getPlan() != null
                && suscripcion.getPlan().getTipo() == TipoPlan.PASE_DIARIO) {
            throw new RuntimeException("Los pases diarios no se pueden congelar: valen solo el día de compra");
        }
        if (!VigenciaSuscripcion.vigente(suscripcion, hoy)) {
            throw new RuntimeException("Solo se pueden congelar suscripciones activas y vigentes");
        }

        suscripcion.setEstado(EstadoSuscripcion.CONGELADA);
        suscripcion.setCongeladaDesde(hoy);
        suscripcionRepository.save(suscripcion);
        return toDTO(suscripcion);
    }

    @SuppressWarnings("null")
    @Transactional
    public SuscripcionResponseDTO descongelar(Long id) {
        Suscripcion suscripcion = suscripcionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Suscripción no encontrada con id: " + id));

        if (suscripcion.getEstado() != EstadoSuscripcion.CONGELADA) {
            throw new RuntimeException("Solo se pueden descongelar suscripciones congeladas");
        }

        LocalDate hoy = LocalDate.now(clock);
        long diasCongelada = 0;
        if (suscripcion.getCongeladaDesde() != null && !suscripcion.getCongeladaDesde().isAfter(hoy)) {
            diasCongelada = java.time.temporal.ChronoUnit.DAYS.between(
                    suscripcion.getCongeladaDesde(), hoy);
        }
        suscripcion.setFechaFin(suscripcion.getFechaFin().plusDays(diasCongelada));
        suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        suscripcion.setCongeladaDesde(null);
        suscripcionRepository.save(suscripcion);
        return toDTO(suscripcion);
    }

    private SuscripcionResponseDTO toDTO(Suscripcion s) {
        // D1-A: el estado expuesto es el derivado (una ACTIVA con fecha pasada
        // se muestra VENCIDA aunque el almacenamiento aún no haya rotado).
        LocalDate hoy = LocalDate.now(clock);
        return SuscripcionResponseDTO.builder()
                .id(s.getId())
                .usuarioId(s.getUsuario().getId())
                .nombreUsuario(s.getUsuario().getNombre())
                .planId(s.getPlan().getId())
                .nombrePlan(s.getPlan().getNombre())
                .fechaInicio(s.getFechaInicio())
                .fechaFin(s.getFechaFin())
                .estado(VigenciaSuscripcion.morosa(s, hoy)
                        ? EstadoSuscripcion.VENCIDA : s.getEstado())
                .creadoEn(s.getCreadoEn())
                .congeladaDesde(s.getCongeladaDesde())
                .build();
    }
}
