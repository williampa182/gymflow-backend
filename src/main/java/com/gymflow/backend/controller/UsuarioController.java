package com.gymflow.backend.controller;

import com.gymflow.backend.dto.CarnetResponseDTO;
import com.gymflow.backend.dto.request.CambioRolRequest;
import com.gymflow.backend.dto.request.CrearClienteRequest;
import com.gymflow.backend.dto.ResultadoImportacionDTO;
import com.gymflow.backend.dto.UsuarioResponseDTO;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UsuarioResponseDTO>> listar(
            @RequestParam(required = false) Rol rol,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(usuarioService.listarUsuarios(rol, q, pageable));
    }

    @GetMapping("/por-documento")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponseDTO> porDocumento(
            @RequestParam String tipo,
            @RequestParam String numero) {
        return ResponseEntity.ok(usuarioService.buscarPorDocumento(tipo, numero));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponseDTO> crear(
            @Valid @RequestBody CrearClienteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(usuarioService.crearCliente(request));
    }

    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResultadoImportacionDTO> importar(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(name = "dryRun", defaultValue = "false") boolean dryRun) {
        if (archivo.isEmpty()) {
            throw new RuntimeException("No se pudo importar: el archivo está vacío");
        }
        try {
            return ResponseEntity.ok(usuarioService.importarSocios(archivo.getInputStream(), dryRun));
        } catch (IOException ex) {
            throw new RuntimeException("No se pudo importar: no se pudo leer el archivo");
        }
    }

    @GetMapping(value = "/plantilla-importacion", produces = "text/csv")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> plantillaImportacion() {
        String plantilla = "nombre,tipoDocumento,numeroDocumento,telefono,email\n"
                + "Ana García,CC,123456789,3001234567,ana@example.com\n"
                + "Bruno López,CE,987654321,,\n";
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"plantilla-socios.csv\"")
                .body(plantilla);
    }

    @PatchMapping("/{id}/rol")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponseDTO> cambiarRol(
            @PathVariable Long id,
            @Valid @RequestBody CambioRolRequest request) {
        return ResponseEntity.ok(usuarioService.cambiarRol(id, request.getRol()));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponseDTO> cambiarEstado(
            @PathVariable Long id,
            @RequestParam boolean activo) {
        return ResponseEntity.ok(usuarioService.cambiarEstado(id, activo));
    }

    @GetMapping("/{id}/carnet")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CarnetResponseDTO> carnet(@PathVariable Long id) {
        // Reimpresión: devuelve también el nombre para que el ADMIN vea de
        // quién es el carnet que está imprimiendo.
        return ResponseEntity.ok(usuarioService.obtenerCarnet(id));
    }

    @PostMapping("/{id}/carnet/rotar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CarnetResponseDTO> rotarCarnet(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.rotarCarnet(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        usuarioService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
