package com.gymflow.backend.service;

import com.gymflow.backend.dto.AsistenciaAcompanadoDTO;
import com.gymflow.backend.dto.AsistenciaResponseDTO;
import com.gymflow.backend.dto.AsistenciaSemanaDTO;
import com.gymflow.backend.dto.CarnetResponseDTO;
import com.gymflow.backend.exception.KioskKeyInvalidaException;
import com.gymflow.backend.model.AsignacionEntrenador;
import com.gymflow.backend.model.Asistencia;
import com.gymflow.backend.model.Suscripcion;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.EstadoSuscripcion;
import com.gymflow.backend.model.enums.MetodoAsistencia;
import com.gymflow.backend.repository.AsignacionEntrenadorRepository;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AsistenciaService {

    private final AsistenciaRepository asistenciaRepository;
    private final SuscripcionRepository suscripcionRepository;
    private final UsuarioRepository usuarioRepository;
    private final KioscoConfigService kioscoConfigService;
    private final AsignacionEntrenadorRepository asignacionEntrenadorRepository;
    private final Clock clock;

    /**
     * Check-in self-service del CLIENTE (reglas 1-5 y 8 de la spec). Orden de
     * validación deliberado: inactivo → 403, sin plan activo → 400, ya
     * registrado → 409. El método lo decide SIEMPRE el servidor (SELF), jamás
     * el body (anti mass-assignment).
     */
    @SuppressWarnings("null")
    @Transactional
    public AsistenciaResponseDTO marcarMi(String email) {
        // Solo alcanzable en carrera (JWT válido + usuario borrado entre el
        // login y el check-in; JwtAuthFilter responde 401 en uso normal). El
        // 404 no está en el contrato, pero es mejor que un 500.
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con email: " + email));

        if (!usuario.isActivo()) {
            throw new RuntimeException("El usuario está dado de baja");
        }

        // "Hoy" sale del Clock de Bogotá (RelojBogotaConfig, regla 7), nunca
        // de LocalDate.now() con la TZ del servidor. El plan debe estar
        // ACTIVA, no vencida y con plan.isActivo() (alineado con
        // EntrenadorService.tienePlanConEntrenadorPersonal).
        LocalDate hoy = LocalDate.now(clock);
        Suscripcion activa = suscripcionRepository
                .findByUsuarioIdAndEstado(usuario.getId(), EstadoSuscripcion.ACTIVA)
                .filter(s -> !s.getFechaFin().isBefore(hoy))
                .filter(s -> s.getPlan() != null && s.getPlan().isActivo())
                .orElse(null);
        if (activa == null) {
            throw new RuntimeException("No tenés un plan activo para registrar tu entrada");
        }

        if (asistenciaRepository.existsByUsuarioIdAndFecha(usuario.getId(), hoy)) {
            throw new RuntimeException("Ya registraste tu entrada hoy");
        }

        Asistencia asistencia;
        try {
            // entradaEn desde el Clock, sin @PrePersist (regla 7): en Railway
            // UTC un check-in a las 23:30 Bogotá quedaría con entradaEn de
            // "mañana" con LocalDateTime.now().
            asistencia = asistenciaRepository.save(Asistencia.builder()
                    .usuario(usuario)
                    .fecha(hoy)
                    .entradaEn(LocalDateTime.now(clock))
                    .metodo(MetodoAsistencia.SELF)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // Red anti-carrera: dos check-ins simultáneos pueden pasar el
            // exists() antes de que cualquiera haga commit. El respaldo real
            // es el índice único uq_asistencia_por_dia (migración 005, y la
            // constraint declarada en la entidad que Hibernate crea en
            // dev/test).
            throw new RuntimeException("Ya registraste tu entrada hoy");
        }
        return toDTO(asistencia);
    }

    /**
     * Check-in del kiosco de recepción (Fase 5, POST /api/asistencias/kiosk,
     * rule 8-10 spec). Identidad por código de carnet (QR/carnet del cliente)
     * + credencial X-Kiosk-Key del dispositivo, validada con BCrypt acá (no
     * en el filtro: el rate limit no gasta un BCrypt por request). El método
     * lo decide SIEMPRE el servidor (KIOSK_CARNET), jamás el body. Orden de
     * validación idéntico a marcarMi: key inválida → 401, inactivo → 403,
     * sin plan activo → 400, ya registrado → 409.
     */
    @SuppressWarnings("null")
    @Transactional
    public AsistenciaResponseDTO marcarKiosk(String codigoCarnet, String apiKey) {
        if (!kioscoConfigService.validar(apiKey)) {
            throw new KioskKeyInvalidaException();
        }

        Usuario usuario = usuarioRepository.findByCodigoCarnet(codigoCarnet)
                .orElseThrow(() -> new RuntimeException(
                        "Código de carnet inválido para el kiosco de recepción"));
        if (usuario.getCodigoCarnet() == null) {
            // Defensa en profundidad (ver miCarnet): nunca debería pasar si el
            // código vino del QR del cliente, pero sin esto un usuario sin
            // código sería tratado como inexistente con mensaje distinto.
            throw new RuntimeException("Código de carnet inválido para el kiosco de recepción");
        }

        if (!usuario.isActivo()) {
            throw new RuntimeException("El usuario está dado de baja");
        }

        LocalDate hoy = LocalDate.now(clock);
        Suscripcion activa = suscripcionRepository
                .findByUsuarioIdAndEstado(usuario.getId(), EstadoSuscripcion.ACTIVA)
                .filter(s -> !s.getFechaFin().isBefore(hoy))
                .filter(s -> s.getPlan() != null && s.getPlan().isActivo())
                .orElse(null);
        if (activa == null) {
            throw new RuntimeException("No tenés un plan activo para registrar tu entrada");
        }

        if (asistenciaRepository.existsByUsuarioIdAndFecha(usuario.getId(), hoy)) {
            throw new RuntimeException("Ya registraste tu entrada hoy");
        }

        Asistencia asistencia;
        try {
            asistencia = asistenciaRepository.save(Asistencia.builder()
                    .usuario(usuario)
                    .fecha(hoy)
                    .entradaEn(LocalDateTime.now(clock))
                    .metodo(MetodoAsistencia.KIOSK_CARNET)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("Ya registraste tu entrada hoy");
        }
        return toDTO(asistencia);
    }

    /**
     * Semana ISO (lunes→domingo) de asistencias del cliente, en hora Bogotá.
     * Transacción read-only explícita: evita LazyInitializationException al
     * leer usuario.getNombre() (LAZY) fuera del alcance del EntityManager.
     */
    @SuppressWarnings("null")
    @Transactional(readOnly = true)
    public AsistenciaSemanaDTO semana(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con email: " + email));

        LocalDate hoy = LocalDate.now(clock);
        LocalDate desde = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate hasta = desde.plusDays(6);

        // ≤7 filas: ordenar en Java es más simple que una query con ORDER BY.
        List<Asistencia> asistencias = asistenciaRepository
                .findByUsuarioIdAndFechaBetween(usuario.getId(), desde, hasta)
                .stream()
                .sorted(Comparator.comparing(Asistencia::getFecha))
                .toList();

        return AsistenciaSemanaDTO.from(desde, hasta, asistencias);
    }

    /**
     * Carnet del CLIENTE autenticado (GET /api/asistencias/mi/carnet). Solo
     * devuelve el código (nunca el de otro usuario); el QR se genera
     * client-side, el backend solo expone el string.
     */
    @SuppressWarnings("null")
    @Transactional(readOnly = true)
    public CarnetResponseDTO miCarnet(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con email: " + email));
        if (usuario.getCodigoCarnet() == null) {
            // Solo alcanzable si AuthService no pudo generar el código o por
            // datos previos a la Fase 5 sin backfill: 404 (recurso inexistente).
            throw new RuntimeException(
                    "Código de carnet no encontrado para el usuario con id: " + usuario.getId());
        }
        return CarnetResponseDTO.builder()
                .codigoCarnet(usuario.getCodigoCarnet())
                .build();
    }

    /**
     * Semana ISO de asistencias de los clientes acompañados por el ENTRENADOR
     * (Fase 5, endpoint #4). Solo asignaciones ACTIVAS (Fase 4). Batch por
     * clienteIds (findByUsuarioIdInAndFechaBetween, sin N+1); un cliente sin
     * asistencias en la semana entra con lista vacía ("no vino esta semana").
     * @SuppressWarnings null: las relaciones LAZY se leen dentro de la
     * transacción read-only (mismo criterio que semana()).
     */
    @SuppressWarnings("null")
    @Transactional(readOnly = true)
    public List<AsistenciaAcompanadoDTO> semanaAcompanados(String email) {
        Usuario entrenador = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con email: " + email));

        List<AsignacionEntrenador> asignaciones = asignacionEntrenadorRepository
                .findByEntrenadorIdAndActivaTrueOrderByAsignadoEnDesc(entrenador.getId());
        if (asignaciones.isEmpty()) {
            return List.of();
        }

        List<Long> clienteIds = asignaciones.stream()
                .map(a -> a.getCliente().getId())
                .toList();
        LocalDate hoy = LocalDate.now(clock);
        LocalDate desde = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate hasta = desde.plusDays(6);

        Map<Long, List<AsistenciaResponseDTO>> porCliente = asistenciaRepository
                .findByUsuarioIdInAndFechaBetween(clienteIds, desde, hasta)
                .stream()
                .collect(Collectors.groupingBy(
                        a -> a.getUsuario().getId(),
                        Collectors.mapping(this::toDTO, Collectors.toList())));

        return asignaciones.stream()
                .map(a -> AsistenciaAcompanadoDTO.builder()
                        .clienteId(a.getCliente().getId())
                        .clienteNombre(a.getCliente().getNombre())
                        .asistencias(porCliente.getOrDefault(a.getCliente().getId(), List.of()))
                        .build())
                .toList();
    }

    /**
     * Check-in manual del ADMIN (recepción/control, Fase 5, endpoint #5).
     * Mismas reglas que marcarMi (1-3 y 5) pero la identidad sale del body
     * usuarioId (el ADMIN decide por quién marca) y el método lo decide el
     * servidor (ADMIN). Duplicado → 409 con mensaje distinto del SELF ("El
     * cliente ya registró su entrada hoy" — el que ya marcó es otro).
     */
    @SuppressWarnings("null")
    @Transactional
    public AsistenciaResponseDTO adminMarcar(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con id: " + usuarioId));

        if (!usuario.isActivo()) {
            throw new RuntimeException("El usuario está dado de baja");
        }

        LocalDate hoy = LocalDate.now(clock);
        Suscripcion activa = suscripcionRepository
                .findByUsuarioIdAndEstado(usuario.getId(), EstadoSuscripcion.ACTIVA)
                .filter(s -> !s.getFechaFin().isBefore(hoy))
                .filter(s -> s.getPlan() != null && s.getPlan().isActivo())
                .orElse(null);
        if (activa == null) {
            throw new RuntimeException("No tenés un plan activo para registrar tu entrada");
        }

        if (asistenciaRepository.existsByUsuarioIdAndFecha(usuario.getId(), hoy)) {
            throw new RuntimeException("El cliente ya registró su entrada hoy");
        }

        Asistencia asistencia;
        try {
            asistencia = asistenciaRepository.save(Asistencia.builder()
                    .usuario(usuario)
                    .fecha(hoy)
                    .entradaEn(LocalDateTime.now(clock))
                    .metodo(MetodoAsistencia.ADMIN)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("El cliente ya registró su entrada hoy");
        }
        return toDTO(asistencia);
    }

    /**
     * Desmarcar (Fase 5, endpoint #6): SOLO la de hoy (regla 6). Otro día →
     * 400 (redacción elegida para NO colisionar con el match 403 de "solo
     * podés"); inexistente → 404. La asistencia de un día pasado es histórica
     * (regla 5: hecho consumado), no se puede borrar.
     */
    @SuppressWarnings("null")
    @Transactional
    public void desmarcar(Long asistenciaId) {
        Asistencia asistencia = asistenciaRepository.findById(asistenciaId)
                .orElseThrow(() -> new RuntimeException(
                        "Asistencia no encontrada con id: " + asistenciaId));

        if (!asistencia.getFecha().equals(LocalDate.now(clock))) {
            throw new RuntimeException("No se puede desmarcar una asistencia de otro día");
        }
        asistenciaRepository.delete(asistencia);
    }

    /**
     * Historial paginado del ADMIN (Fase 5, endpoint #7). Page directa del
     * repositorio; un usuario sin asistencias o inexistente devuelve página
     * vacía → 200 (el contrato solo contempla 200).
     */
    @SuppressWarnings("null")
    @Transactional(readOnly = true)
    public Page<AsistenciaResponseDTO> historial(Long usuarioId, Pageable pageable) {
        return asistenciaRepository.findByUsuarioId(usuarioId, pageable).map(this::toDTO);
    }

    private AsistenciaResponseDTO toDTO(Asistencia a) {
        return AsistenciaResponseDTO.builder()
                .id(a.getId())
                .usuarioId(a.getUsuario().getId())
                .nombre(a.getUsuario().getNombre())
                .fecha(a.getFecha())
                .entradaEn(a.getEntradaEn())
                .salidaEn(a.getSalidaEn())
                .metodo(a.getMetodo())
                .build();
    }
}
