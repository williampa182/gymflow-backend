package com.gymflow.backend.service;

import com.gymflow.backend.dto.CarnetResponseDTO;
import com.gymflow.backend.dto.UsuarioResponseDTO;
import com.gymflow.backend.model.Rutina;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.repository.AsignacionEntrenadorRepository;
import com.gymflow.backend.repository.AsignacionRutinaRepository;
import com.gymflow.backend.repository.AsistenciaRepository;
import com.gymflow.backend.repository.RutinaRepository;
import com.gymflow.backend.repository.SuscripcionRepository;
import com.gymflow.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final CodigoCarnetGenerator codigoCarnetGenerator;
    private final AsistenciaRepository asistenciaRepository;
    private final AsignacionRutinaRepository asignacionRutinaRepository;
    private final RutinaRepository rutinaRepository;
    private final AsignacionEntrenadorRepository asignacionEntrenadorRepository;
    private final SuscripcionRepository suscripcionRepository;

    public Page<UsuarioResponseDTO> listarUsuarios(Rol rol, Pageable pageable) {
        Page<Usuario> usuarios = (rol != null)
                ? usuarioRepository.findByRol(rol, pageable)
                : usuarioRepository.findAll(pageable);

        return usuarios.map(this::toDTO);
    }

    @SuppressWarnings("null")
    @Transactional
    public UsuarioResponseDTO cambiarEstado(Long id, boolean activo) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con id: " + id));

        if (!activo && usuario.isActivo() && usuario.getRol() == Rol.ADMIN) {
            asegurarQueNoSeaUltimoAdminActivo();
        }

        usuario.setActivo(activo);
        usuarioRepository.save(usuario);
        return toDTO(usuario);
    }

    @SuppressWarnings("null")
    @Transactional
    public UsuarioResponseDTO cambiarRol(Long id, Rol rol) {
        if (rol == null) {
            throw new RuntimeException("El rol es obligatorio");
        }

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con id: " + id));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && usuario.getEmail().equals(authentication.getName())) {
            throw new RuntimeException("No puedes cambiar tu propio rol");
        }

        if (usuario.isActivo()
                && usuario.getRol() == Rol.ADMIN
                && rol != Rol.ADMIN) {
            asegurarQueNoSeaUltimoAdminActivo();
        }

        usuario.setRol(rol);
        usuarioRepository.save(usuario);
        return toDTO(usuario);
    }

    /**
     * Borrado físico de un usuario (DELETE /api/usuarios/{id}, ADMIN).
     * El orden hijos→padre replica el script de limpieza de prod
     * (scripts/limpiar_usuarios_prueba.sql): las FKs son NO ACTION, no hay
     * cascada a nivel BD. El borrado es transaccional — si algo falla a
     * mitad de camino, ROLLBACK completo.
     *
     * Protecciones: no auto-borrado (quien se autenticó no puede
     * borrarse) y no se puede borrar el último ADMIN activo (mismo
     * criterio que cambiarEstado/cambiarRol).
     */
    @SuppressWarnings("null")
    @Transactional
    public void eliminar(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con id: " + id));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && usuario.getEmail().equals(authentication.getName())) {
            throw new RuntimeException("No puedes borrar tu propio usuario");
        }

        if (usuario.isActivo() && usuario.getRol() == Rol.ADMIN) {
            asegurarQueNoSeaUltimoAdminActivo();
        }

        // Hijos: asistencias (check-ins) del usuario.
        asistenciaRepository.deleteByUsuarioId(id);

        // Hijos: asignaciones de rutinas donde el usuario es CLIENTE y las
        // rutinas creadas por él (como ENTRENADOR) con sus asignaciones y
        // ejercicios.
        List<Long> rutinasPropias = rutinaRepository
                .findByEntrenadorIdOrderByCreadoEnDesc(id)
                .stream()
                .map(Rutina::getId)
                .toList();
        if (!rutinasPropias.isEmpty()) {
            asignacionRutinaRepository.deleteByRutinaIdIn(rutinasPropias);
        }
        asignacionRutinaRepository.deleteByClienteId(id);
        rutinaRepository.deleteByEntrenadorId(id);

        // Hijos: acompañamientos de entrenador (ambos lados de la relación).
        asignacionEntrenadorRepository.deleteByClienteIdOrEntrenadorId(id, id);

        // Hijos: suscripciones.
        suscripcionRepository.deleteByUsuarioId(id);

        // Padre: el usuario.
        usuarioRepository.delete(usuario);
    }

    /**
     * Vista ADMIN del carnet (reimpresión; también para usuarios inactivos).
     * El "sin código" es un 404 (recurso no existe), nunca un 500.
     */
    @SuppressWarnings("null")
    @Transactional(readOnly = true)
    public CarnetResponseDTO obtenerCarnet(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con id: " + id));
        if (usuario.getCodigoCarnet() == null) {
            throw new RuntimeException("Código de carnet no encontrado para el usuario con id: " + id);
        }
        return CarnetResponseDTO.builder()
                .codigoCarnet(usuario.getCodigoCarnet())
                .nombre(usuario.getNombre())
                .build();
    }

    /**
     * Rotación por pérdida (POST /api/usuarios/{id}/carnet/rotar, ADMIN).
     * Gating estricto contra el índice único: si el generador agota los
     * reintentos lanza (500) y el código anterior queda intacto. El código
     * NUNCA es clave de asistencias: rotarlo no toca el historial.
     */
    @SuppressWarnings("null")
    @Transactional
    public CarnetResponseDTO rotarCarnet(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con id: " + id));
        String nuevoCodigo = codigoCarnetGenerator.generarUnico(
                codigo -> usuarioRepository.existsByCodigoCarnet(codigo));
        usuario.setCodigoCarnet(nuevoCodigo);
        usuarioRepository.save(usuario);
        return CarnetResponseDTO.builder().codigoCarnet(nuevoCodigo).build();
    }

    private void asegurarQueNoSeaUltimoAdminActivo() {
        // Serializa las operaciones que podrían reducir el conjunto de ADMIN
        // activos; el count aislado tendría una ventana TOCTOU bajo concurrencia.
        usuarioRepository.findByRolAndActivoForUpdate(Rol.ADMIN, true);
        if (usuarioRepository.countByRolAndActivo(Rol.ADMIN, true) <= 1) {
            throw new RuntimeException("No se puede quitar el último ADMIN activo");
        }
    }

    private UsuarioResponseDTO toDTO(Usuario u) {
        return UsuarioResponseDTO.builder()
                .id(u.getId())
                .nombre(u.getNombre())
                .email(u.getEmail())
                .rol(u.getRol())
                .activo(u.isActivo())
                .creadoEn(u.getCreadoEn())
                .build();
    }
}
