package com.gymflow.backend.repository;

import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.repository.projection.UsuarioPorRolProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    boolean existsByEmail(String email);
    List<Usuario> findByRol(Rol rol);
    Page<Usuario> findByRol(Rol rol, Pageable pageable);

    @Query("""
            select u.rol as rol, count(u) as cantidad
            from Usuario u
            where u.activo = true
            group by u.rol
            """)
    List<UsuarioPorRolProjection> contarUsuariosActivosPorRol();
}
