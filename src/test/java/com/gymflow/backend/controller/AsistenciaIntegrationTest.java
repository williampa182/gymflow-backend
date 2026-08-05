package com.gymflow.backend.controller;

import com.gymflow.backend.dto.AsistenciaAcompanadoDTO;
import com.gymflow.backend.dto.AsistenciaResponseDTO;
import com.gymflow.backend.dto.AsistenciaSemanaDTO;
import com.gymflow.backend.dto.CarnetResponseDTO;
import com.gymflow.backend.dto.KioscoConfigResponseDTO;
import com.gymflow.backend.dto.KioscoKeyResponseDTO;
import com.gymflow.backend.dto.PlanResponseDTO;
import com.gymflow.backend.dto.SuscripcionRequestDTO;
import com.gymflow.backend.dto.SuscripcionResponseDTO;
import com.gymflow.backend.dto.dashboard.AsistenciasSemanaStatsDTO;
import com.gymflow.backend.dto.response.AuthResponse;
import com.gymflow.backend.model.AsignacionEntrenador;
import com.gymflow.backend.model.Asistencia;
import com.gymflow.backend.model.KioscoConfig;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.MetodoAsistencia;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.repository.AsignacionEntrenadorRepository;
import com.gymflow.backend.repository.AsistenciaRepository;
import com.gymflow.backend.repository.KioscoConfigRepository;
import com.gymflow.backend.repository.PlanRepository;
import com.gymflow.backend.repository.SuscripcionRepository;
import com.gymflow.backend.repository.UsuarioRepository;
import com.gymflow.backend.security.jwt.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 5 P2: check-in self-service del CLIENTE contra la BD real (Postgres vía
 * Docker). Flujo: registro → plan → suscripción ACTIVA con inicio hoy Bogotá →
 * POST /mi 201 (SELF) → duplicado 409 → semana 200 lunes→domingo → sin
 * suscripción 400 → cliente dado de baja 403 → ADMIN 403 (rol).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(properties = "app.security.auth-rate-limit.max-per-minute=1000")
class AsistenciaIntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PlanRepository planRepository;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private AsistenciaRepository asistenciaRepository;
    @Autowired
    private AsignacionEntrenadorRepository asignacionEntrenadorRepository;
    @Autowired
    private KioscoConfigRepository kioscoConfigRepository;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long clienteId;
    private Long clienteSinSuscripcionId;
    private Long entrenadorId;
    private Long asignacionId;
    private Long planId;
    private Long suscripcionId;
    private Long asistenciaId;
    private Long asistenciaOtroDiaId;
    private Long adminId;
    private String adminToken;

    @AfterEach
    void limpiarDatosDePrueba() {
        // Reverso de la creación: asistencia → asignación → suscripción →
        // plan → usuarios.
        if (asistenciaOtroDiaId != null) {
            asistenciaRepository.deleteById(asistenciaOtroDiaId);
        }
        if (asistenciaId != null) {
            asistenciaRepository.deleteById(asistenciaId);
        }
        if (asignacionId != null) {
            asignacionEntrenadorRepository.deleteById(asignacionId);
        }
        if (suscripcionId != null) {
            suscripcionRepository.deleteById(suscripcionId);
        }
        if (planId != null) {
            planRepository.deleteById(planId);
        }
        if (clienteSinSuscripcionId != null) {
            usuarioRepository.deleteById(clienteSinSuscripcionId);
        }
        if (clienteId != null) {
            usuarioRepository.deleteById(clienteId);
        }
        if (entrenadorId != null) {
            usuarioRepository.deleteById(entrenadorId);
        }
        if (adminId != null) {
            usuarioRepository.deleteById(adminId);
        }
        // La fila única del kiosco la deja rotar() de la IT: se limpia para
        // devolver al estado "no configurado" (fail-closed por defecto).
        kioscoConfigRepository.deleteById(KioscoConfig.FILA_UNICA_ID);
    }

    @Test
    @SuppressWarnings("null")
    void flujoCheckIn_selfServiceYsemana() {
        // Antes de registrar el primer usuario: si no existiera ningún ADMIN
        // activo, /register promueve al primero a ADMIN y el rol CLIENTE del
        // flujo no se cumple.
        tokenDeAdmin();
        AuthResponse cliente = registrar("cliente", "CLIENTE");
        clienteId = cliente.getId();
        PlanResponseDTO plan = crearPlan();
        planId = plan.getId();
        crearSuscripcionActiva(clienteId, planId);

        LocalDate hoy = LocalDate.now(BOGOTA);
        LocalDate lunes = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        // Primer check-in del día → 201; metodo lo decide el servidor (SELF).
        ResponseEntity<AsistenciaResponseDTO> checkIn = post(
                "/api/asistencias/mi", cliente.getToken(), null, AsistenciaResponseDTO.class);
        assertThat(checkIn.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(checkIn.getBody().getMetodo()).isEqualTo(MetodoAsistencia.SELF);
        assertThat(checkIn.getBody().getNombre()).isEqualTo("Cliente Fase5");
        assertThat(checkIn.getBody().getFecha()).isEqualTo(hoy);
        asistenciaId = checkIn.getBody().getId();

        // Repetir el mismo día → 409 (check-then-act + índice único).
        ResponseEntity<Map> duplicado = post(
                "/api/asistencias/mi", cliente.getToken(), null, Map.class);
        assertThat(duplicado.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Semana ISO lunes→domingo en hora Bogotá.
        ResponseEntity<AsistenciaSemanaDTO> semana = get(
                "/api/asistencias/mi/semana", cliente.getToken(), AsistenciaSemanaDTO.class);
        assertThat(semana.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(semana.getBody().getFechaDesde()).isEqualTo(lunes);
        assertThat(semana.getBody().getFechaHasta()).isEqualTo(lunes.plusDays(6));
        assertThat(semana.getBody().getTotal()).isGreaterThanOrEqualTo(1);
        assertThat(semana.getBody().getAsistencias())
                .extracting(AsistenciaResponseDTO::getFecha)
                .contains(hoy);
        assertThat(semana.getBody().getAsistencias())
                .allMatch(a -> a.getMetodo() == MetodoAsistencia.SELF);

        // Cliente sin suscripción ACTIVA → 400.
        AuthResponse sinPlan = registrar("cliente-sin-plan", "CLIENTE");
        clienteSinSuscripcionId = sinPlan.getId();
        ResponseEntity<Map> sinSuscripcion = post(
                "/api/asistencias/mi", sinPlan.getToken(), null, Map.class);
        assertThat(sinSuscripcion.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Cliente dado de baja → 403. Alcanzable porque esTokenValido no mira
        // isEnabled(): el JWT sigue autenticando y el corte es del service.
        Usuario usuarioCliente = usuarioRepository.findById(clienteId).orElseThrow();
        usuarioCliente.setActivo(false);
        usuarioRepository.save(usuarioCliente);
        ResponseEntity<Map> dadoDeBaja = post(
                "/api/asistencias/mi", cliente.getToken(), null, Map.class);
        assertThat(dadoDeBaja.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        usuarioCliente.setActivo(true);
        usuarioRepository.save(usuarioCliente);

        // ADMIN no puede usar el check-in del cliente → 403 (barrera de rol).
        ResponseEntity<Map> prohibido = post(
                "/api/asistencias/mi", adminToken, null, Map.class);
        assertThat(prohibido.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * Fase 5 P3: carnet digital. El registro genera el código con gating
     * estricto; el CLIENTE lo consulta en /mi/carnet (solo el código, sin
     * datos de otros); el ADMIN lo reimprime (con nombre) y lo rota. Rotar
     * cambia el código en las tres vistas y el viejo deja de corresponder. Un
     * usuario sin código (creado directo en BD, simulando datos pre-Fase 5)
     * responde 404, nunca 500.
     */
    @Test
    @SuppressWarnings("null")
    void carnet_generacionVistaYRotacion() {
        tokenDeAdmin();
        AuthResponse cliente = registrar("cliente", "CLIENTE");
        clienteId = cliente.getId();

        // El CLIENTE ve su carnet: solo el código, del alfabeto sin ambiguos.
        ResponseEntity<CarnetResponseDTO> mi = get(
                "/api/asistencias/mi/carnet", cliente.getToken(), CarnetResponseDTO.class);
        assertThat(mi.getStatusCode()).isEqualTo(HttpStatus.OK);
        String codigoOriginal = mi.getBody().getCodigoCarnet();
        assertThat(codigoOriginal).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6,8}");
        assertThat(mi.getBody().getNombre()).isNull();

        // El ADMIN reimprime el carnet del cliente (con nombre) → mismo código.
        ResponseEntity<CarnetResponseDTO> impresion = get(
                "/api/usuarios/" + clienteId + "/carnet", adminToken, CarnetResponseDTO.class);
        assertThat(impresion.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(impresion.getBody().getCodigoCarnet()).isEqualTo(codigoOriginal);
        assertThat(impresion.getBody().getNombre()).isEqualTo("Cliente Fase5");

        // El CLIENTE no puede ver el carnet de otros → 403 (ruta de ADMIN).
        ResponseEntity<Map> prohibido = get(
                "/api/usuarios/" + clienteId + "/carnet", cliente.getToken(), Map.class);
        assertThat(prohibido.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Rotación por pérdida → código nuevo, distinto, consistente en ambas
        // vistas; el viejo deja de corresponder.
        ResponseEntity<CarnetResponseDTO> rotado = post(
                "/api/usuarios/" + clienteId + "/carnet/rotar", adminToken, null, CarnetResponseDTO.class);
        assertThat(rotado.getStatusCode()).isEqualTo(HttpStatus.OK);
        String codigoNuevo = rotado.getBody().getCodigoCarnet();
        assertThat(codigoNuevo).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6,8}");
        assertThat(codigoNuevo).isNotEqualTo(codigoOriginal);

        ResponseEntity<CarnetResponseDTO> trasRotar = get(
                "/api/asistencias/mi/carnet", cliente.getToken(), CarnetResponseDTO.class);
        assertThat(trasRotar.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(trasRotar.getBody().getCodigoCarnet()).isEqualTo(codigoNuevo);
        assertThat(trasRotar.getBody().getCodigoCarnet()).isNotEqualTo(codigoOriginal);

        ResponseEntity<CarnetResponseDTO> reimpreso = get(
                "/api/usuarios/" + clienteId + "/carnet", adminToken, CarnetResponseDTO.class);
        assertThat(reimpreso.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reimpreso.getBody().getCodigoCarnet()).isEqualTo(codigoNuevo);

        // Usuario sin código (el ADMIN de tokenDeAdmin se crea directo, sin
        // campo) → 404, nunca 500.
        ResponseEntity<Map> sinCodigo = get(
                "/api/usuarios/" + adminId + "/carnet", adminToken, Map.class);
        assertThat(sinCodigo.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Fase 5 P4: kiosco de recepción. La siembra del entorno es blank en
     * tests (fail-closed), así que la clave nace de la rotación ADMIN (#12),
     * que crea la fila única si falta. Flujo: rotar → key1; sin key → 401;
     * key inválida → 401; check-in con key1 → 201 (KIOSK_CARNET, método
     * decidido por el servidor); duplicado → 409; código inexistente → 400
     * (anti-enumeración); rotar → key2 distinta → la key1 ya no sirve → 401.
     */
    @Test
    @SuppressWarnings("null")
    void flujoKiosco_checkInYRotacionDeLaKey() {
        tokenDeAdmin();
        AuthResponse cliente = registrar("cliente", "CLIENTE");
        clienteId = cliente.getId();
        planId = crearPlan().getId();
        crearSuscripcionActiva(clienteId, planId);
        String codigo = get("/api/asistencias/mi/carnet", cliente.getToken(), CarnetResponseDTO.class)
                .getBody().getCodigoCarnet();

        // 1) Rotar la clave inicial (no hay siembra en test) y ver el estado.
        ResponseEntity<KioscoKeyResponseDTO> primera = post(
                "/api/kiosco/config/rotar", adminToken, null, KioscoKeyResponseDTO.class);
        assertThat(primera.getStatusCode()).isEqualTo(HttpStatus.OK);
        String key1 = primera.getBody().getKey();
        ResponseEntity<KioscoConfigResponseDTO> configuracion = get(
                "/api/kiosco/config", adminToken, KioscoConfigResponseDTO.class);
        assertThat(configuracion.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(configuracion.getBody().isConfigurada()).isTrue();

        // 2) Sin X-Kiosk-Key → 401 (valida credencial antes que código).
        ResponseEntity<Map> sinKey = postAlKiosk(codigo, null, Map.class);
        assertThat(sinKey.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 3) Key inválida → 401.
        ResponseEntity<Map> keyInvalida = postAlKiosk(codigo, "clave-incorrecta", Map.class);
        assertThat(keyInvalida.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 4) Check-in válido → 201 con metodo KIOSK_CARNET decidido por el server.
        ResponseEntity<AsistenciaResponseDTO> checkIn = postAlKiosk(codigo, key1, AsistenciaResponseDTO.class);
        assertThat(checkIn.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(checkIn.getBody().getMetodo()).isEqualTo(MetodoAsistencia.KIOSK_CARNET);
        assertThat(checkIn.getBody().getNombre()).isEqualTo("Cliente Fase5");
        asistenciaId = checkIn.getBody().getId();

        // 5) El mismo código otra vez el mismo día → 409.
        ResponseEntity<Map> duplicado = postAlKiosk(codigo, key1, Map.class);
        assertThat(duplicado.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // 6) Código inexistente → 400 genérico (no 404, no revela el código).
        ResponseEntity<Map> codigoInvalido = postAlKiosk("ZZZZZZ", key1, Map.class);
        assertThat(codigoInvalido.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 7) Rotación → key2; la anterior deja de ser válida de inmediato.
        ResponseEntity<KioscoKeyResponseDTO> segunda = post(
                "/api/kiosco/config/rotar", adminToken, null, KioscoKeyResponseDTO.class);
        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.OK);
        String key2 = segunda.getBody().getKey();
        assertThat(key2).isNotEqualTo(key1);

        ResponseEntity<Map> keyVieja = postAlKiosk(codigo, key1, Map.class);
        assertThat(keyVieja.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<Map> keyNueva = postAlKiosk(codigo, key2, Map.class);
        // el cliente ya marcó hoy → 409 (la clave sigue siendo válida)
        assertThat(keyNueva.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    /**
     * Fase 5 P5: control de acceso del ADMIN y semana del ENTRENADOR.
     * El ADMIN marca la asistencia del cliente (#5) → 201 con metodo ADMIN;
     * duplicado → 409; historial paginado (#7) → 200; #13 dashboard de la
     * semana → 200 con hoy contado; el ENTRENADOR ve la semana de sus
     * acompañados (#4) → 200; barrera de rol: el ENTRENADOR no toca
     * /admin/** → 403; desmarcar la de hoy (#6) → 204; la de otro día → 400.
     */
    @Test
    @SuppressWarnings("null")
    void adminMarcaYentrenadorVeLaSemana() {
        tokenDeAdmin();
        AuthResponse cliente = registrar("cliente", "CLIENTE");
        clienteId = cliente.getId();
        planId = crearPlan().getId();
        crearSuscripcionActiva(clienteId, planId);
        AuthResponse entrenador = registrar("entrenador", "ENTRENADOR");
        entrenadorId = entrenador.getId();
        Usuario clienteEntity = usuarioRepository.findById(clienteId).orElseThrow();
        Usuario entrenadorEntity = usuarioRepository.findById(entrenadorId).orElseThrow();
        asignacionId = asignacionEntrenadorRepository.save(AsignacionEntrenador.builder()
                .cliente(clienteEntity)
                .entrenador(entrenadorEntity)
                .build()).getId();

        LocalDate hoy = LocalDate.now(BOGOTA);

        // #5 El ADMIN marca por id del cliente → 201; metodo lo decide el server.
        ResponseEntity<AsistenciaResponseDTO> marcada = post(
                "/api/asistencias/admin/marcar", adminToken,
                Map.of("usuarioId", clienteId), AsistenciaResponseDTO.class);
        assertThat(marcada.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(marcada.getBody().getMetodo()).isEqualTo(MetodoAsistencia.ADMIN);
        assertThat(marcada.getBody().getNombre()).isEqualTo("Cliente Fase5");
        assertThat(marcada.getBody().getFecha()).isEqualTo(hoy);
        asistenciaId = marcada.getBody().getId();

        // El mismo día, otra vez → 409 (check-then-act + índice único).
        ResponseEntity<Map> duplicado = post(
                "/api/asistencias/admin/marcar", adminToken,
                Map.of("usuarioId", clienteId), Map.class);
        assertThat(duplicado.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // #7 Historial paginado del cliente → 200 con esa asistencia.
        ResponseEntity<Map> historial = restTemplate.exchange(
                "/api/asistencias/admin/historial?usuarioId=" + clienteId + "&page=0&size=20",
                HttpMethod.GET, new HttpEntity<>(headersCon(adminToken)), Map.class);
        assertThat(historial.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(historial.getBody()).containsEntry("totalElements", 1);

        // #13 Dashboard ADMIN: asistencias de la semana → 200, hoy contado.
        ResponseEntity<AsistenciasSemanaStatsDTO> dashboard = get(
                "/api/dashboard/admin/asistencias-semana", adminToken, AsistenciasSemanaStatsDTO.class);
        assertThat(dashboard.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dashboard.getBody().asistenciasHoy()).isGreaterThanOrEqualTo(1);
        assertThat(dashboard.getBody().asistenciasSemana())
                .extracting(AsistenciasSemanaStatsDTO.AsistenciaDiaStat::fecha)
                .contains(hoy);

        // #4 El ENTRENADOR ve la semana de sus acompañados (asignación ACTIVA).
        ResponseEntity<List<AsistenciaAcompanadoDTO>> semana = restTemplate.exchange(
                "/api/asistencias/acompanados/semana", HttpMethod.GET,
                new HttpEntity<>(headersCon(entrenador.getToken())),
                new ParameterizedTypeReference<List<AsistenciaAcompanadoDTO>>() {});
        assertThat(semana.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(semana.getBody()).hasSize(1);
        assertThat(semana.getBody().getFirst().getClienteNombre()).isEqualTo("Cliente Fase5");
        assertThat(semana.getBody().getFirst().getAsistencias()).hasSize(1);

        // Barrera de rol: el ENTRENADOR no toca las rutas ADMIN → 403.
        ResponseEntity<Map> prohibidoMarcar = post(
                "/api/asistencias/admin/marcar", entrenador.getToken(),
                Map.of("usuarioId", clienteId), Map.class);
        assertThat(prohibidoMarcar.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<Map> prohibidoEliminar = delete(
                "/api/asistencias/" + asistenciaId, entrenador.getToken(), Map.class);
        assertThat(prohibidoEliminar.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // #6 Desmarcar la de hoy → 204.
        ResponseEntity<Void> desmarcada = delete(
                "/api/asistencias/" + asistenciaId, adminToken, Void.class);
        assertThat(desmarcada.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        asistenciaId = null;

        // Una asistencia de otro día no se desmarca (control de acceso, no
        // editar el registro histórico) → 400.
        asistenciaOtroDiaId = asistenciaRepository.save(Asistencia.builder()
                .usuario(clienteEntity)
                .fecha(hoy.minusDays(1))
                .entradaEn(hoy.minusDays(1).atTime(8, 0))
                .metodo(MetodoAsistencia.ADMIN)
                .build()).getId();
        ResponseEntity<Map> deOtroDia = delete(
                "/api/asistencias/" + asistenciaOtroDiaId, adminToken, Map.class);
        assertThat(deOtroDia.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private HttpHeaders headersCon(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private AuthResponse registrar(String sufijo, String rol) {
        String email = sufijo + "-fase5-" + System.nanoTime() + "@gymflow.test";
        String nombre = sufijo.equals("cliente") ? "Cliente Fase5" : "Cliente Sin Plan Fase5";
        String payload = """
                {"nombre": "%s", "email": "%s", "password": "PasswordSegura2026!", "rol": "%s"}
                """.formatted(nombre, email, rol);
        ResponseEntity<AuthResponse> response = post(
                "/api/auth/register", null, payload, AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private PlanResponseDTO crearPlan() {
        String payload = """
                {
                  "nombre": "Plan Fase5 %s",
                  "precio": 90000,
                  "duracionDias": 30,
                  "tipo": "MENSUAL"
                }
                """.formatted(System.nanoTime());
        ResponseEntity<PlanResponseDTO> response = post(
                "/api/planes", tokenDeAdmin(), payload, PlanResponseDTO.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private String tokenDeAdmin() {
        if (adminToken != null) {
            return adminToken;
        }
        String email = "admin-fase5-" + System.nanoTime() + "@gymflow.test";
        Usuario admin = usuarioRepository.save(Usuario.builder()
                .nombre("Admin Fase5")
                .email(email)
                .password(passwordEncoder.encode("PasswordSegura2026!"))
                .rol(Rol.ADMIN)
                .build());
        adminId = admin.getId();
        adminToken = jwtUtil.generarToken(admin);
        return adminToken;
    }

    private void crearSuscripcionActiva(Long clienteId, Long planId) {
        SuscripcionRequestDTO request = new SuscripcionRequestDTO();
        request.setUsuarioId(clienteId);
        request.setPlanId(planId);
        request.setFechaInicio(LocalDate.now(BOGOTA));
        ResponseEntity<SuscripcionResponseDTO> response = post(
                "/api/suscripciones", tokenDeAdmin(), request, SuscripcionResponseDTO.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        suscripcionId = response.getBody().getId();
    }

    private <T> ResponseEntity<T> get(String path, String token, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }

    private <T> ResponseEntity<T> postAlKiosk(String codigo, String apiKey, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null) {
            headers.set("X-Kiosk-Key", apiKey);
        }
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("codigo", codigo), headers);
        return restTemplate.exchange("/api/asistencias/kiosk", HttpMethod.POST, entity, responseType);
    }

    private <T> ResponseEntity<T> post(String path, String token, Object payload, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        HttpEntity<?> entity = payload == null ? new HttpEntity<>(headers)
                : new HttpEntity<>(payload, headers);
        return restTemplate.exchange(path, HttpMethod.POST, entity, responseType);
    }

    private <T> ResponseEntity<T> delete(String path, String token, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers), responseType);
    }
}
