package com.gymflow.backend.controller;

import com.gymflow.backend.dto.PlanRequestDTO;
import com.gymflow.backend.dto.PlanResponseDTO;
import com.gymflow.backend.dto.RutinaResponseDTO;
import com.gymflow.backend.dto.SuscripcionRequestDTO;
import com.gymflow.backend.dto.SuscripcionResponseDTO;
import com.gymflow.backend.dto.response.AuthResponse;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.repository.AsignacionEntrenadorRepository;
import com.gymflow.backend.repository.AsignacionRutinaRepository;
import com.gymflow.backend.repository.AsistenciaRepository;
import com.gymflow.backend.repository.PlanRepository;
import com.gymflow.backend.repository.RutinaRepository;
import com.gymflow.backend.repository.SuscripcionRepository;
import com.gymflow.backend.repository.UsuarioRepository;
import com.gymflow.backend.security.jwt.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Borrado de usuarios (DELETE /api/usuarios/{id}, ADMIN) contra la BD real:
 * valida el orden de borrado de FKs hijos→padre (asistencias, asignaciones
 * de rutinas de ambos lados, rutinas con ejercicios, acompañamientos y
 * suscripciones) y la barrera de auto-borrado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(properties = "app.security.auth-rate-limit.max-per-minute=1000")
class UsuarioBorradoIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PlanRepository planRepository;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private RutinaRepository rutinaRepository;
    @Autowired
    private AsignacionRutinaRepository asignacionRutinaRepository;
    @Autowired
    private AsignacionEntrenadorRepository asignacionEntrenadorRepository;
    @Autowired
    private AsistenciaRepository asistenciaRepository;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long adminId;
    private Long planId;

    @AfterEach
    void limpiarDatosDePrueba() {
        if (planId != null) {
            planRepository.deleteById(planId);
        }
        if (adminId != null) {
            usuarioRepository.deleteById(adminId);
        }
    }

    @Test
    @SuppressWarnings("null")
    void eliminarClienteConDependencias_borraTodoElGrafo() {
        AuthResponse entrenador = registrar("entrenador", "ENTRENADOR");
        AuthResponse cliente = registrar("cliente", "CLIENTE");
        String adminToken = tokenDeAdmin();

        PlanResponseDTO plan = crearPlanConAcompanamiento();
        planId = plan.getId();
        crearSuscripcionActiva(cliente.getId(), plan.getId());

        // Acompañamiento del entrenador al cliente.
        ResponseEntity<Void> acompanado = post(
                "/api/entrenador/asignarme/" + cliente.getId(), entrenador.getToken(), null, Void.class);
        assertThat(acompanado.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Rutina del entrenador asignada al cliente.
        String payloadRutina = """
                {
                  "nombre": "Rutina Para Borrar",
                  "descripcion": "Fixture de borrado",
                  "ejercicios": [{"nombre": "Press banca", "series": 3, "repeticiones": 10}]
                }
                """;
        ResponseEntity<RutinaResponseDTO> rutina = post(
                "/api/rutinas", entrenador.getToken(), payloadRutina, RutinaResponseDTO.class);
        assertThat(rutina.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long rutinaId = rutina.getBody().id();
        ResponseEntity<Void> asignada = post(
                "/api/rutinas/" + rutinaId + "/asignar/" + cliente.getId(),
                entrenador.getToken(), null, Void.class);
        assertThat(asignada.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Check-in del cliente marcado por el ADMIN para que tenga asistencias.
        ResponseEntity<Map> checkin = post(
                "/api/asistencias/admin/marcar", adminToken,
                Map.of("usuarioId", cliente.getId()), Map.class);
        assertThat(checkin.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(asistenciaRepository.findByUsuarioId(cliente.getId(), org.springframework.data.domain.Pageable.unpaged())
                .getTotalElements()).isEqualTo(1);

        // El ADMIN borra al CLIENTE → 204.
        ResponseEntity<Void> borrado = delete(
                "/api/usuarios/" + cliente.getId(), adminToken, Void.class);
        assertThat(borrado.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // El cliente ya no existe y sus hijos se fueron con él.
        assertThat(usuarioRepository.findById(cliente.getId())).isEmpty();
        assertThat(suscripcionRepository.findByUsuarioId(cliente.getId())).isEmpty();
        assertThat(asistenciaRepository.findByUsuarioId(cliente.getId(), org.springframework.data.domain.Pageable.unpaged())
                .getContent()).isEmpty();
        assertThat(asignacionEntrenadorRepository
                .findByClienteIdOrderByAsignadoEnDesc(cliente.getId())).isEmpty();
        // La asignación de rutina del cliente se borró, pero la rutina sigue
        // (pertenece al entrenador, que no fue borrado).
        assertThat(asignacionRutinaRepository.findByClienteIdAndRutinaId(cliente.getId(), rutinaId)).isEmpty();
        assertThat(rutinaRepository.findById(rutinaId)).isPresent();

        // El ADMIN borra al ENTRENADOR → 204 (rutinas y ejercicios se van).
        ResponseEntity<Void> borradoEntrenador = delete(
                "/api/usuarios/" + entrenador.getId(), adminToken, Void.class);
        assertThat(borradoEntrenador.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(usuarioRepository.findById(entrenador.getId())).isEmpty();
        assertThat(rutinaRepository.findById(rutinaId)).isEmpty();
        assertThat(asignacionEntrenadorRepository
                .findByEntrenadorIdAndActivaTrueOrderByAsignadoEnDesc(entrenador.getId())).isEmpty();
    }

    @Test
    @SuppressWarnings("null")
    void eliminar_autoBorradoDelAdminLogueado_rechazadoCon400() {
        String email = "admin-autoborrado-" + System.nanoTime() + "@gymflow.test";
        Usuario admin = usuarioRepository.save(Usuario.builder()
                .nombre("Admin AutoBorrado")
                .email(email)
                .password(passwordEncoder.encode("PasswordSegura2026!"))
                .rol(Rol.ADMIN)
                .build());
        Long id = admin.getId();
        adminId = id;
        String token = jwtUtil.generarToken(admin);

        ResponseEntity<Map> respuesta = delete("/api/usuarios/" + id, token, Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(usuarioRepository.findById(id)).isPresent();
    }

    private AuthResponse registrar(String sufijo, String rol) {
        String email = sufijo + "-borrado-" + System.nanoTime() + "@gymflow.test";
        String payload = """
                {"nombre": "%s", "email": "%s", "password": "PasswordSegura2026!", "rol": "%s"}
                """.formatted(sufijo.equals("cliente") ? "Cliente Borrado" : "Entrenador Borrado", email, rol);
        ResponseEntity<AuthResponse> response = post(
                "/api/auth/register", null, payload, AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private PlanResponseDTO crearPlanConAcompanamiento() {
        PlanRequestDTO request = new PlanRequestDTO();
        request.setNombre("Plan Borrado " + System.nanoTime());
        request.setPrecio(java.math.BigDecimal.valueOf(90000));
        request.setDuracionDias(30);
        request.setTipo(com.gymflow.backend.model.enums.TipoPlan.MENSUAL);
        request.setIncluyeEntrenadorPersonal(true);
        ResponseEntity<PlanResponseDTO> response = post(
                "/api/planes", tokenDeAdmin(), request, PlanResponseDTO.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private String tokenDeAdmin() {
        String email = "admin-borrado-" + System.nanoTime() + "@gymflow.test";
        Usuario admin = usuarioRepository.save(Usuario.builder()
                .nombre("Admin Borrado")
                .email(email)
                .password(passwordEncoder.encode("PasswordSegura2026!"))
                .rol(Rol.ADMIN)
                .build());
        adminId = admin.getId();
        return jwtUtil.generarToken(admin);
    }

    private void crearSuscripcionActiva(Long clienteId, Long planId) {
        SuscripcionRequestDTO request = new SuscripcionRequestDTO();
        request.setUsuarioId(clienteId);
        request.setPlanId(planId);
        request.setFechaInicio(LocalDate.now());
        ResponseEntity<SuscripcionResponseDTO> response = post(
                "/api/suscripciones", tokenDeAdmin(), request, SuscripcionResponseDTO.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private <T> ResponseEntity<T> get(String path, String token, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), responseType);
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
