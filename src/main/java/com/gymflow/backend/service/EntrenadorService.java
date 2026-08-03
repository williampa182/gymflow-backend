package com.gymflow.backend.service;

import com.gymflow.backend.dto.ClienteElegibleDTO;
import com.gymflow.backend.dto.HistorialAcompanamientoDTO;
import com.gymflow.backend.dto.MiEntrenadorDTO;
import com.gymflow.backend.model.AsignacionEntrenador;
import com.gymflow.backend.model.Suscripcion;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.EstadoSuscripcion;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.repository.AsignacionEntrenadorRepository;
import com.gymflow.backend.repository.SuscripcionRepository;
import com.gymflow.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Acompañamiento (Fase 4). El entrenador se asigna como acompañante de un
 * CLIENTE cuyo plan ACTIVO incluye entrenador personal. Regla derivada,
 * no negocio manual: la elegibilidad se calcula en el momento desde las
 * suscripciones — si el plan no incluye acompañamiento, el cliente deja de
 * aparecer como elegible (y quien ya lo acompañaba lo ve, pero el cliente
 * conserva su rutina hasta que el entrenador la quite o la desactive).
 */
@Service
@RequiredArgsConstructor
public class EntrenadorService {

    private final UsuarioRepository usuarioRepository;
    private final SuscripcionRepository suscripcionRepository;
    private final AsignacionEntrenadorRepository asignacionEntrenadorRepository;

    /**
     * CLIENTES activos con plan activo que incluye entrenador personal, con
     * la marca de si el entrenador autenticado ya los acompaña. El email de
     * los clientes nunca se expone.
     */
    @Transactional(readOnly = true)
    public List<ClienteElegibleDTO> listarClientesElegibles(String emailEntrenador) {
        Usuario entrenador = usuarioRepository.findByEmail(emailEntrenador).orElseThrow();
        // Una sola query de acompañados del entrenador → mapa por cliente
        // (evita N+1 contra findByClienteIdAndActivaTrue por cada candidato).
        Map<Long, AsignacionEntrenador> acompanados = new HashMap<>();
        for (AsignacionEntrenador asignacion
                : asignacionEntrenadorRepository.findByEntrenadorIdAndActivaTrueOrderByAsignadoEnDesc(entrenador.getId())) {
            acompanados.put(asignacion.getCliente().getId(), asignacion);
        }

        List<Usuario> clientesActivos = usuarioRepository.findByRolAndActivo(Rol.CLIENTE, true);
        // Una sola query de suscripciones ACTIVAS para todos los candidatos
        // → mapa por usuario (aplanado; antes era un query por cliente).
        Map<Long, Suscripcion> activasPorCliente = new HashMap<>();
        for (Suscripcion suscripcion : suscripcionRepository.findByEstadoAndUsuarioIdIn(
                EstadoSuscripcion.ACTIVA, clientesActivos.stream().map(Usuario::getId).toList())) {
            activasPorCliente.put(suscripcion.getUsuario().getId(), suscripcion);
        }

        List<ClienteElegibleDTO> elegibles = new ArrayList<>();
        for (Usuario cliente : clientesActivos) {
            if (tienePlanConEntrenadorPersonal(activasPorCliente.get(cliente.getId()))) {
                elegibles.add(ClienteElegibleDTO.from(cliente, acompanados.get(cliente.getId())));
            }
        }
        return elegibles;
    }

    /**
     * Asignarme como acompañante de un cliente elegible. Check-then-act con
     * red de seguridad a nivel BD: el índice único parcial
     * uq_acompanante_activo_por_cliente (migración 004) rechaza la segunda
     * asignación ACTIVA concurrente, y el 409 de GlobalExceptionHandler la
     * comunica con el mismo mensaje que el chequeo.
     */
    @Transactional
    public void asignarme(String emailEntrenador, Long clienteId) {
        Usuario entrenador = usuarioRepository.findByEmail(emailEntrenador).orElseThrow();
        if (clienteId.equals(entrenador.getId())) {
            throw new IllegalArgumentException("no podés ser tu propio acompañante");
        }
        Usuario cliente = usuarioRepository.findById(clienteId)
                .orElseThrow(() -> new IllegalArgumentException("cliente no encontrado"));
        if (cliente.getRol() != Rol.CLIENTE || !cliente.isActivo()) {
            throw new IllegalArgumentException("el usuario no es un cliente activo");
        }
        if (!tienePlanConEntrenadorPersonal(clienteId)) {
            throw new IllegalArgumentException("el plan del cliente no incluye entrenador personal");
        }
        if (asignacionEntrenadorRepository.findByClienteIdAndActivaTrue(clienteId).isPresent()) {
            throw new IllegalArgumentException("el cliente ya tiene una asignación activa");
        }
        asignacionEntrenadorRepository.save(AsignacionEntrenador.builder()
                .cliente(cliente)
                .entrenador(entrenador)
                .build());
    }

    @Transactional
    public void cancelar(String emailEntrenador, Long asignacionId) {
        Usuario entrenador = usuarioRepository.findByEmail(emailEntrenador).orElseThrow();
        AsignacionEntrenador asignacion =
                asignacionEntrenadorRepository.findByIdAndEntrenadorId(asignacionId, entrenador.getId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "solo el entrenador de la asignación puede cancelarla"));
        asignacion.setActiva(false);
        asignacionEntrenadorRepository.save(asignacion);
    }

    @Transactional(readOnly = true)
    public Optional<MiEntrenadorDTO> miEntrenador(String emailCliente) {
        Usuario cliente = usuarioRepository.findByEmail(emailCliente).orElseThrow();
        return asignacionEntrenadorRepository.findByClienteIdAndActivaTrue(cliente.getId())
                .map(MiEntrenadorDTO::from);
    }

    /**
     * Historial completo de acompañamientos del cliente autenticado:
     * asignaciones ACTIVAS y canceladas, más reciente primero. Nunca
     * expone el email del entrenador.
     */
    @Transactional(readOnly = true)
    public List<HistorialAcompanamientoDTO> miHistorial(String emailCliente) {
        Usuario cliente = usuarioRepository.findByEmail(emailCliente).orElseThrow();
        return asignacionEntrenadorRepository.findByClienteIdOrderByAsignadoEnDesc(cliente.getId())
                .stream().map(HistorialAcompanamientoDTO::from).toList();
    }

    private boolean tienePlanConEntrenadorPersonal(Suscripcion activa) {
        return activa != null
                && activa.getPlan() != null
                && activa.getPlan().isActivo()
                && activa.getPlan().isIncluyeEntrenadorPersonal();
    }

    private boolean tienePlanConEntrenadorPersonal(Long clienteId) {
        return suscripcionRepository.findByUsuarioIdAndEstado(clienteId, EstadoSuscripcion.ACTIVA)
                .map(Suscripcion::getPlan)
                .map(plan -> plan.isActivo() && plan.isIncluyeEntrenadorPersonal())
                .orElse(false);
    }
}
