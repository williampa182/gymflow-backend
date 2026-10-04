package com.gymflow.backend.service;

import com.gymflow.backend.dto.CarnetResponseDTO;
import com.gymflow.backend.dto.UsuarioResponseDTO;
import com.gymflow.backend.dto.ResultadoImportacionDTO;
import com.gymflow.backend.dto.request.CrearClienteRequest;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final CodigoCarnetGenerator codigoCarnetGenerator;
    private final PasswordEncoder passwordEncoder;
    private final jakarta.validation.Validator validator;
    private final AsistenciaRepository asistenciaRepository;
    private final AsignacionRutinaRepository asignacionRutinaRepository;
    private final RutinaRepository rutinaRepository;
    private final AsignacionEntrenadorRepository asignacionEntrenadorRepository;
    private final SuscripcionRepository suscripcionRepository;

    public UsuarioResponseDTO buscarPorDocumento(String tipo, String numero) {
        String tipoNormalizado = tipo == null ? "" : tipo.trim().toUpperCase();
        String numeroNormalizado = numero == null ? "" : numero.trim().toUpperCase();
        return usuarioRepository
                .findByTipoDocumentoAndNumeroDocumento(tipoNormalizado, numeroNormalizado)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException(
                        "No se encontró ningún socio con ese documento"));
    }

    public Page<UsuarioResponseDTO> listarUsuarios(Rol rol, String textoBusqueda, Pageable pageable) {        // D1-B: con texto se busca (paginado); sin texto, comportamiento
        // anterior intacto para el resto de consumidores.
        String q = textoBusqueda == null ? "" : textoBusqueda.trim();
        Page<Usuario> usuarios;
        if (q.isEmpty()) {
            usuarios = (rol != null)
                    ? usuarioRepository.findByRol(rol, pageable)
                    : usuarioRepository.findAll(pageable);
        } else {
            if (q.length() > 50) {
                q = q.substring(0, 50);
            }
            usuarios = usuarioRepository.buscarPorTexto(rol, escaparLike(q), pageable);
        }

        return usuarios.map(this::toDTO);
    }

    /**
     * D1-B: \% y \_ se buscarían como comodines sin esto ("50%" traería
     * "500", "_" traería todo). Se escapan para búsqueda literal.
     */
    static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
                .tipoDocumento(u.getTipoDocumento())
                .numeroDocumento(u.getNumeroDocumento())
                .telefono(u.getTelefono())
                .build();
    }

    @SuppressWarnings("null")
    @Transactional
    public UsuarioResponseDTO crearCliente(CrearClienteRequest request) {
        String email = normalizarEmail(request.getEmail());
        String tipo = request.getTipoDocumento().trim().toUpperCase();
        String numero = request.getNumeroDocumento().trim().toUpperCase();
        verificarDuplicados(email, tipo, numero);
        String codigoCarnet = codigoCarnetGenerator.generarUnico(
                codigo -> usuarioRepository.existsByCodigoCarnet(codigo));
        Usuario usuario = Usuario.builder()
                .nombre(request.getNombre().trim())
                .email(email)
                .password(passwordEncoder.encode(generarPasswordAleatoria()))
                .rol(Rol.CLIENTE)
                .codigoCarnet(codigoCarnet)
                .tipoDocumento(tipo)
                .numeroDocumento(numero)
                .telefono(request.getTelefono() == null ? null : request.getTelefono().trim())
                .build();
        usuarioRepository.save(usuario);
        return toDTO(usuario);
    }

    private void verificarDuplicados(String email, String tipo, String numero) {
        if (email != null && usuarioRepository.existsByEmail(email)) {
            throw new RuntimeException("Ya existe un usuario con ese email");
        }
        if (usuarioRepository.existsByTipoDocumentoAndNumeroDocumento(tipo, numero)) {
            throw new RuntimeException("Ya existe un usuario con ese documento");
        }
    }

    private static String normalizarEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim();
    }

    private static String generarPasswordAleatoria() {
        String alfabeto = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom aleatorio = new SecureRandom();
        StringBuilder sb = new StringBuilder(24);
        for (int i = 0; i < 24; i++) {
            sb.append(alfabeto.charAt(aleatorio.nextInt(alfabeto.length())));
        }
        return sb.toString();
    }

    private static final int MAX_FILAS_IMPORTACION = 500;
    private static final String[] HEADER_IMPORTACION = {
        "nombre", "tipodocumento", "numerodocumento", "telefono", "email" };

    @SuppressWarnings("null")
    public ResultadoImportacionDTO importarSocios(InputStream csv, boolean dryRun) {
        List<String[]> filas;
        try {
            filas = leerCsv(csv);
        } catch (IOException | CsvValidationException ex) {
            throw new RuntimeException("No se pudo importar: el archivo no es un CSV válido");
        }
        if (filas.isEmpty()) {
            throw new RuntimeException("No se pudo importar: el archivo está vacío");
        }
        validarHeader(filas.get(0));
        List<String[]> datos = filas.subList(1, filas.size()).stream()
                .filter(f -> !(f.length == 0 || (f.length == 1 && f[0].isBlank())))
                .toList();
        if (datos.size() > MAX_FILAS_IMPORTACION) {
            throw new RuntimeException(
                    "No se pudo importar: máximo " + MAX_FILAS_IMPORTACION + " filas por archivo");
        }
        List<ResultadoImportacionDTO.FilaOmitidaDTO> omitidos = new ArrayList<>();
        List<ResultadoImportacionDTO.FilaOmitidaDTO> errores = new ArrayList<>();
        Set<String> documentosVistos = new HashSet<>();
        Set<String> emailsVistos = new HashSet<>();
        int creados = 0;
        for (int i = 0; i < datos.size(); i++) {
            int numeroFila = i + 2;
            CrearClienteRequest pedido = filaAPedido(datos.get(i));
            String fallas = validarPedido(pedido);
            if (fallas != null) {
                errores.add(new ResultadoImportacionDTO.FilaOmitidaDTO(numeroFila, fallas));
                continue;
            }
            String documento = pedido.getTipoDocumento().trim().toUpperCase()
                    + "|" + pedido.getNumeroDocumento().trim().toUpperCase();
            String email = pedido.getEmail() == null || pedido.getEmail().isBlank()
                    ? null : pedido.getEmail().trim();
            if (!documentosVistos.add(documento)
                    || (email != null && !emailsVistos.add(email.toLowerCase()))) {
                omitidos.add(new ResultadoImportacionDTO.FilaOmitidaDTO(
                        numeroFila, "Duplicado dentro del archivo"));
                continue;
            }
            try {
                if (dryRun) {
                    verificarDuplicados(
                            normalizarEmail(pedido.getEmail()),
                            pedido.getTipoDocumento().trim().toUpperCase(),
                            pedido.getNumeroDocumento().trim().toUpperCase());
                } else {
                    crearCliente(pedido);
                }
                creados++;
            } catch (RuntimeException ex) {
                String mensaje = ex.getMessage() != null ? ex.getMessage() : "Error inesperado";
                if (mensaje.toLowerCase().contains("ya existe")) {
                    omitidos.add(new ResultadoImportacionDTO.FilaOmitidaDTO(numeroFila, mensaje));
                } else {
                    errores.add(new ResultadoImportacionDTO.FilaOmitidaDTO(numeroFila, mensaje));
                }
            }
        }
        return new ResultadoImportacionDTO(datos.size(), creados, omitidos, errores);
    }

    private static List<String[]> leerCsv(InputStream csv) throws IOException, CsvValidationException {
        byte[] bytes = csv.readAllBytes();
        String texto = new String(bytes, StandardCharsets.UTF_8);
        if (!texto.isEmpty() && texto.charAt(0) == '\uFEFF') {
            texto = texto.substring(1);
        }
        String primeraLinea = texto.lines().filter(l -> !l.isBlank()).findFirst().orElse("");
        char separador = primeraLinea.contains(";") ? ';' : ',';
        List<String[]> filas = new ArrayList<>();
        try (Reader reader = new java.io.StringReader(texto);
                CSVReader csvReader = new CSVReaderBuilder(reader)
                        .withCSVParser(new CSVParserBuilder().withSeparator(separador).build())
                        .build()) {
            String[] fila;
            while ((fila = csvReader.readNext()) != null) {
                filas.add(fila);
            }
        }
        return filas;
    }

    private static void validarHeader(String[] header) {
        String[] normalizado = java.util.Arrays.stream(header)
                .map(c -> c == null ? "" : c.trim().toLowerCase().replace(" ", ""))
                .toArray(String[]::new);
        if (!java.util.Arrays.equals(normalizado, HEADER_IMPORTACION)) {
            throw new RuntimeException(
                    "No se pudo importar: el header debe ser "
                            + "nombre,tipoDocumento,numeroDocumento,telefono,email");
        }
    }

    private static CrearClienteRequest filaAPedido(String[] fila) {
        CrearClienteRequest pedido = new CrearClienteRequest();
        pedido.setNombre(celda(fila, 0));
        pedido.setTipoDocumento(celda(fila, 1));
        pedido.setNumeroDocumento(celda(fila, 2));
        pedido.setTelefono(celda(fila, 3).isEmpty() ? null : celda(fila, 3));
        pedido.setEmail(celda(fila, 4).isEmpty() ? null : celda(fila, 4));
        return pedido;
    }

    private static String celda(String[] fila, int indice) {
        return indice < fila.length && fila[indice] != null ? fila[indice].trim() : "";
    }

    private String validarPedido(CrearClienteRequest pedido) {
        var violaciones = validator.validate(pedido);
        if (violaciones.isEmpty()) {
            return null;
        }
        return violaciones.stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .sorted()
                .reduce((a, b) -> a + "; " + b)
                .orElse("Fila inválida");
    }
}
