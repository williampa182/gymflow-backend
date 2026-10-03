package com.gymflow.backend.repository;

import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.repository.projection.UsuarioPorRolProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByCodigoCarnet(String codigoCarnet);
    boolean existsByTipoDocumentoAndNumeroDocumento(String tipoDocumento, String numeroDocumento);
    Optional<Usuario> findByTipoDocumentoAndNumeroDocumento(String tipoDocumento, String numeroDocumento);
    // Kiosco (Fase 5): el código de carnet resuelve al usuario en el momento del
    // ingreso; nunca es clave de asistencias (la rotación no toca historial).
    Optional<Usuario> findByCodigoCarnet(String codigoCarnet);
    List<Usuario> findByRol(Rol rol);
    Page<Usuario> findByRol(Rol rol, Pageable pageable);

    /**
     * D1-B: búsqueda por texto para selectores con más de una página de
     * usuarios (el modal de suscripciones pedía /usuarios sin size y solo
     * veía los primeros 20). ILIKE sobre nombre/email; el llamador escapa
     * \, % y _ y la query declara ESCAPE para que se busquen literales.
     */
    @Query("""
            select u from Usuario u
            where (:rol is null or u.rol = :rol)
              and (lower(u.nombre) like lower(concat('%', :q, '%')) escape '\\'
                or lower(u.email) like lower(concat('%', :q, '%')) escape '\\')
            """)
    Page<Usuario> buscarPorTexto(
            @Param("rol") Rol rol,
            @Param("q") String textoEscapado,
            Pageable pageable);
    List<Usuario> findByRolAndActivo(Rol rol, boolean activo);
    long countByRolAndActivo(Rol rol, boolean activo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.rol = :rol and u.activo = :activo")
    List<Usuario> findByRolAndActivoForUpdate(
            @Param("rol") Rol rol,
            @Param("activo") boolean activo);

    @Query("""
            select u.rol as rol, count(u) as cantidad
            from Usuario u
            where u.activo = true
            group by u.rol
            """)
    List<UsuarioPorRolProjection> contarUsuariosActivosPorRol();
}
