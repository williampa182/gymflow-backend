package com.gymflow.backend.service;

import com.gymflow.backend.dto.*;
import com.gymflow.backend.model.*;
import com.gymflow.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rutinas de entrenamiento (Fase 4). Ownership estricto: las rutinas
 * pertenecen al ENTRENADOR que las creó (identity.getName() — nunca un id
 * del body) y solo él puede editarlas, desactivarlas o asignarlas.
 * Un CLIENTE solo puede leer las rutinas que su acompañante le asignó.
 */
@Service
@RequiredArgsConstructor
public class RutinaService {

    private final RutinaRepository rutinaRepository;
    private final AsignacionRutinaRepository asignacionRutinaRepository;
    private final AsignacionEntrenadorRepository asignacionEntrenadorRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<RutinaResponseDTO> listarMias(String emailEntrenador) {
        Usuario entrenador = usuarioRepository.findByEmail(emailEntrenador).orElseThrow();
        List<Rutina> rutinas = rutinaRepository.findByEntrenadorIdOrderByCreadoEnDesc(entrenador.getId());
        // Una sola query de asignaciones con cliente resuelto → mapa por
        // rutina (evita N+1 al leer el nombre de cada cliente asignado).
        Map<Long, List<ClienteAsignadoDTO>> asignadosPorRutina = new HashMap<>();
        for (AsignacionRutina asignacion
                : asignacionRutinaRepository.findByRutinaEntrenadorId(entrenador.getId())) {
            asignadosPorRutina.computeIfAbsent(asignacion.getRutina().getId(), k -> new ArrayList<>())
                    .add(ClienteAsignadoDTO.from(asignacion.getCliente()));
        }
        return rutinas.stream()
                .map(rutina -> RutinaResponseDTO.from(rutina,
                        asignadosPorRutina.getOrDefault(rutina.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RutinaResponseDTO> listarAsignadas(String emailCliente) {
        Usuario cliente = usuarioRepository.findByEmail(emailCliente).orElseThrow();
        return rutinaRepository.findRutinasActivasAsignadas(cliente.getId())
                .stream().map(RutinaResponseDTO::from).toList();
    }

    @Transactional
    public RutinaResponseDTO crear(String emailEntrenador, RutinaRequestDTO request) {
        Usuario entrenador = usuarioRepository.findByEmail(emailEntrenador).orElseThrow();
        Rutina rutina = Rutina.builder()
                .entrenador(entrenador)
                .nombre(request.nombre())
                .descripcion(request.descripcion())
                .build();
        rutina.reemplazarEjercicios(mapearEjercicios(request));
        return RutinaResponseDTO.from(rutinaRepository.save(rutina));
    }

    @Transactional
    public RutinaResponseDTO actualizar(String emailEntrenador, Long rutinaId, RutinaRequestDTO request) {
        Rutina rutina = obtenerPropia(emailEntrenador, rutinaId);
        rutina.setNombre(request.nombre());
        rutina.setDescripcion(request.descripcion());
        rutina.reemplazarEjercicios(mapearEjercicios(request));
        return RutinaResponseDTO.from(rutinaRepository.save(rutina));
    }

    @Transactional
    public void desactivar(String emailEntrenador, Long rutinaId) {
        Rutina rutina = obtenerPropia(emailEntrenador, rutinaId);
        rutina.setActivo(false);
        rutinaRepository.save(rutina);
        // No se borran las asignaciones: el historial queda, y el cliente
        // simplemente deja de verla (findRutinasActivasAsignadas filtra activo).
    }

    /**
     * El entrenador asigna una rutina propia a un cliente que acompaña.
     * Check-then-act: verifica ownership de la rutina, que el cliente esté
     * en su lista de acompañados y que no tenga ya esa rutina.
     */
    @Transactional
    public void asignar(String emailEntrenador, Long rutinaId, Long clienteId) {
        Rutina rutina = obtenerPropia(emailEntrenador, rutinaId);
        if (!rutina.isActivo()) {
            throw new IllegalArgumentException("solo puedes asignar rutinas activas");
        }
        if (!asignacionEntrenadorRepository.findByClienteIdAndActivaTrue(clienteId)
                .filter(asignacion -> asignacion.getEntrenador().getId().equals(rutina.getEntrenador().getId()))
                .isPresent()) {
            throw new IllegalArgumentException("solo puedes asignar rutinas a tus clientes acompañados");
        }
        if (asignacionRutinaRepository.existsByClienteIdAndRutinaId(clienteId, rutinaId)) {
            throw new IllegalArgumentException("el cliente ya tiene esta rutina asignada");
        }
        Usuario cliente = usuarioRepository.findById(clienteId).orElseThrow();
        asignacionRutinaRepository.save(
                AsignacionRutina.builder().cliente(cliente).rutina(rutina).build());
    }

    @Transactional
    public void quitar(String emailEntrenador, Long rutinaId, Long clienteId) {
        obtenerPropia(emailEntrenador, rutinaId);
        // Idempotente: si la asignación no existe, no hay nada que quitar.
        asignacionRutinaRepository.findByClienteIdAndRutinaId(clienteId, rutinaId)
                .ifPresent(asignacionRutinaRepository::delete);
    }

    private Rutina obtenerPropia(String emailEntrenador, Long rutinaId) {
        Usuario entrenador = usuarioRepository.findByEmail(emailEntrenador).orElseThrow();
        return rutinaRepository.findByIdAndEntrenadorId(rutinaId, entrenador.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "solo el entrenador creador puede modificar esta rutina"));
    }

    private List<Ejercicio> mapearEjercicios(RutinaRequestDTO request) {
        List<Ejercicio> ejercicios = new ArrayList<>();
        int orden = 1;
        for (EjercicioRequestDTO dto : request.ejercicios()) {
            ejercicios.add(Ejercicio.builder()
                    .id(dto.id())
                    .nombre(dto.nombre())
                    .series(dto.series())
                    .repeticiones(dto.repeticiones())
                    .orden(orden)
                    .build());
            orden++;
        }
        return ejercicios;
    }
}
