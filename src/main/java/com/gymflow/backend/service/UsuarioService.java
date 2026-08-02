package com.gymflow.backend.service;

import com.gymflow.backend.dto.UsuarioResponseDTO;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

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
