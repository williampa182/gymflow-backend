package com.gymflow.backend.controller;

import com.gymflow.backend.dto.ClienteElegibleDTO;
import com.gymflow.backend.dto.MiEntrenadorDTO;
import com.gymflow.backend.dto.PlanRequestDTO;
import com.gymflow.backend.dto.PlanResponseDTO;
import com.gymflow.backend.dto.RutinaResponseDTO;
import com.gymflow.backend.dto.SuscripcionRequestDTO;
import com.gymflow.backend.dto.SuscripcionResponseDTO;
import com.gymflow.backend.dto.response.AuthResponse;
import com.gymflow.backend.model.AsignacionEntrenador;
import com.gymflow.backend.model.AsignacionRutina;
import com.gymflow.backend.model.Ejercicio;
import com.gymflow.backend.model.Plan;
import com.gymflow.backend.model.Rutina;
import com.gymflow.backend.model.Suscripcion;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.repository.AsignacionEntrenadorRepository;
import com.gymflow.backend.repository.AsignacionRutinaRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flujo completo de la Fase 4 (entrenador↔cliente) contra la BD real:
 * registro de roles → plan con acompañamiento → suscripción ACTIVA →
 * acompañamiento → rutina con ejercicios → asignación → lectura del
 * cliente. Incluye las barreras: duplicado de acompañamiento (409) y
 * rutina duplicada (409).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class EntrenadorRutinaIntegrationTest {

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
    private JwtUtil jwtUtil;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long entrenadorId;
    private Long clienteId;
    private Long planId;
    private Long suscripcionId;
    private Long rutinaId;
    private Long adminId;
    private String adminToken;

    @AfterEach
    void limpiarDatosDePrueba() {
        if (rutinaId != null) {
            asignacionRutinaRepository.findByClienteIdAndRutinaId(clienteId, rutinaId)
                    .ifPresent(asignacionRutinaRepository::delete);
            rutinaRepository.deleteById(rutinaId);
        }
        asignacionEntrenadorRepository.findByClienteIdAndActivaTrue(clienteId)
                .ifPresent(asignacionEntrenadorRepository::delete);
        if (suscripcionId != null) {
            suscripcionRepository.deleteById(suscripcionId);
        }
        if (planId != null) {
            planRepository.deleteById(planId);
        }
        if (entrenadorId != null) {
            usuarioRepository.deleteById(entrenadorId);
        }
        if (clienteId != null) {
            usuarioRepository.deleteById(clienteId);
        }
        if (adminId != null) {
            usuarioRepository.deleteById(adminId);
        }
    }

    @Test
    @SuppressWarnings("null")
    void flujoCompleto_acompañamientoYrutinas() {
        AuthResponse entrenador = registrar("entrenador", "ENTRENADOR");
        AuthResponse cliente = registrar("cliente", "CLIENTE");
        entrenadorId = entrenador.getId();
        clienteId = cliente.getId();
        PlanResponseDTO plan = crearPlanConAcompañamiento();
        planId = plan.getId();
        crearSuscripcionActiva(clienteId, planId);
        // El cliente elegible aparece sin marca de acompañamiento.
        ResponseEntity<ClienteElegibleDTO[]> elegibles = get(
                "/api/entrenador/clientes-elegibles", entrenador.getToken(), ClienteElegibleDTO[].class);
        assertThat(elegibles.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(elegibles.getBody())
                .extracting(ClienteElegibleDTO::nombre)
                .contains("Cliente Fase4");
        assertThat(elegibles.getBody()).noneMatch(ClienteElegibleDTO::yaAcompaño);

        // Acompañamiento.
        ResponseEntity<Void> asignado = post(
                "/api/entrenador/asignarme/" + clienteId, entrenador.getToken(), null, Void.class);
        assertThat(asignado.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Duplicar el acompañamiento → 409 (check-then-act + índice único parcial).
        ResponseEntity<Map> duplicado = post(
                "/api/entrenador/asignarme/" + clienteId, entrenador.getToken(), null, Map.class);
        assertThat(duplicado.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Ahora sí marca yaAcompaño.
        ResponseEntity<ClienteElegibleDTO[]> trasAcompañar = get(
                "/api/entrenador/clientes-elegibles", entrenador.getToken(), ClienteElegibleDTO[].class);
        assertThat(trasAcompañar.getBody()).anyMatch(ClienteElegibleDTO::yaAcompaño);

        // Crear rutina con un ejercicio.
        String payloadRutina = """
                {
                  "nombre": "Fase4 Full Body",
                  "descripcion": "Rutina de integración",
                  "ejercicios": [{"nombre": "Press banca", "series": 3, "repeticiones": 10}]
                }
                """;
        ResponseEntity<RutinaResponseDTO> rutina = post(
                "/api/rutinas", entrenador.getToken(), payloadRutina, RutinaResponseDTO.class);
        assertThat(rutina.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        rutinaId = rutina.getBody().id();
        assertThat(rutina.getBody().ejercicios()).hasSize(1);
        assertThat(rutina.getBody().ejercicios().getFirst().orden()).isEqualTo(1);

        // Asignar la rutina al cliente acompañado.
        ResponseEntity<Void> asignada = post(
                "/api/rutinas/" + rutinaId + "/asignar/" + clienteId, entrenador.getToken(), null, Void.class);
        assertThat(asignada.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Asignarla de nuevo → 409 "ya tiene".
        ResponseEntity<Map> repetida = post(
                "/api/rutinas/" + rutinaId + "/asignar/" + clienteId, entrenador.getToken(), null, Map.class);
        assertThat(repetida.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // El cliente la ve.
        ResponseEntity<RutinaResponseDTO[]> misRutinas = get(
                "/api/rutinas/mias", cliente.getToken(), RutinaResponseDTO[].class);
        assertThat(misRutinas.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(misRutinas.getBody())
                .extracting(RutinaResponseDTO::nombre)
                .contains("Fase4 Full Body");

        // El cliente ve a su acompañante.
        ResponseEntity<MiEntrenadorDTO> miEntrenador = get(
                "/api/entrenador/mio", cliente.getToken(), MiEntrenadorDTO.class);
        assertThat(miEntrenador.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(miEntrenador.getBody().nombre()).isEqualTo("Entrenador Fase4");

        // El cliente NO puede listar las rutinas del entrenador (barrera de rol).
        ResponseEntity<Map> prohibido = get("/api/rutinas", cliente.getToken(), Map.class);
        assertThat(prohibido.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private AuthResponse registrar(String sufijo, String rol) {
        String email = sufijo + "-fase4-" + System.nanoTime() + "@gymflow.test";
        String nombre = sufijo.equals("cliente") ? "Cliente Fase4" : "Entrenador Fase4";
        String payload = """
                {"nombre": "%s", "email": "%s", "password": "PasswordSegura2026!", "rol": "%s"}
                """.formatted(nombre, email, rol);
        ResponseEntity<AuthResponse> response = post(
                "/api/auth/register", null, payload, AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private PlanResponseDTO crearPlanConAcompañamiento() {
        String payload = """
                {
                  "nombre": "Plan Fase4 %s",
                  "precio": 90000,
                  "duracionDias": 30,
                  "tipo": "MENSUAL",
                  "incluyeEntrenadorPersonal": true
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
        String email = "admin-fase4-" + System.nanoTime() + "@gymflow.test";
        Usuario admin = usuarioRepository.save(Usuario.builder()
                .nombre("Admin Fase4")
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
        request.setFechaInicio(LocalDate.now());
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
}
