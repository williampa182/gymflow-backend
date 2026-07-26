package com.gymflow.backend.controller;

import com.gymflow.backend.dto.PlanRequestDTO;
import com.gymflow.backend.dto.PlanResponseDTO;
import com.gymflow.backend.dto.SuscripcionRequestDTO;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.repository.PlanRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regresiones del hallazgo 3.2 del THREAT_MODEL.md: los DTOs de escritura no
 * deben aceptar campos controlados por el servidor. La verificación HTTP usa
 * JSON crudo para cubrir también el binding de Jackson y el mapeo al modelo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class DtoMassAssignmentRegressionTest {

    private static final List<String> CAMPOS_PROHIBIDOS = List.of(
            "id", "activo", "creadoEn", "fechaFin");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

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
    void dtosDeEscrituraNoDeclaranCamposControladosPorElServidor() {
        assertThat(nombresDeCampos(PlanRequestDTO.class))
                .as("PlanRequestDTO no debe aceptar campos administrados por el servidor")
                .doesNotContainAnyElementsOf(CAMPOS_PROHIBIDOS);
        assertThat(nombresDeCampos(SuscripcionRequestDTO.class))
                .as("SuscripcionRequestDTO no debe aceptar campos administrados por el servidor")
                .doesNotContainAnyElementsOf(CAMPOS_PROHIBIDOS);
    }

    @Test
    void crearPlanConActivoEnPayloadCrudo_guardaPlanActivo() {
        String nombrePlan = "Plan mass-assignment " + System.nanoTime();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokenDeAdmin());

        String payloadConCampoControlado = """
                {
                  "nombre": "%s",
                  "descripcion": "Payload de regresion",
                  "precio": 99000,
                  "duracionDias": 30,
                  "tipo": "MENSUAL",
                  "limiteClases": 8,
                  "activo": false
                }
                """.formatted(nombrePlan);

        ResponseEntity<PlanResponseDTO> response = restTemplate.postForEntity(
                "/api/planes",
                new HttpEntity<>(payloadConCampoControlado, headers),
                PlanResponseDTO.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        planId = response.getBody().getId();
        assertThat(response.getBody().isActivo())
                .as("el campo activo enviado por el cliente no debe reflejarse en la respuesta")
                .isTrue();
        assertThat(planRepository.findById(response.getBody().getId()))
                .as("el campo activo enviado por el cliente no debe persistirse en la entidad")
                .get()
                .extracting(plan -> plan.isActivo())
                .isEqualTo(true);
    }

    private List<String> nombresDeCampos(Class<?> dtoClass) {
        return Arrays.stream(dtoClass.getDeclaredFields())
                .map(Field::getName)
                .toList();
    }

    private String tokenDeAdmin() {
        String email = "mass-assignment-admin-" + System.nanoTime() + "@gymflow.com";
        Usuario admin = usuarioRepository.save(Usuario.builder()
                .nombre("Admin Regresion")
                .email(email)
                .password(passwordEncoder.encode("PasswordSegura2026!"))
                .rol(Rol.ADMIN)
                .build());
        adminId = admin.getId();
        return jwtUtil.generarToken(admin);
    }
}
