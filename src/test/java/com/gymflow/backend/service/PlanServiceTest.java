package com.gymflow.backend.service;

import com.gymflow.backend.dto.PlanRequestDTO;
import com.gymflow.backend.dto.PlanResponseDTO;
import com.gymflow.backend.model.Plan;
import com.gymflow.backend.model.enums.TipoPlan;
import com.gymflow.backend.repository.PlanRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private PlanRepository planRepository;

    @InjectMocks
    private PlanService planService;

    private Plan plan;
    private PlanRequestDTO request;

    @BeforeEach
    void setUp() {
        plan = Plan.builder()
                .id(1L)
                .nombre("Plan Mensual")
                .descripcion("Acceso completo")
                .precio(new BigDecimal("50000"))
                .duracionDias(30)
                .tipo(TipoPlan.MENSUAL)
                .incluyeClases(true)
                .incluyeEntrenadorPersonal(false)
                .activo(true)
                .build();

        request = new PlanRequestDTO();
        request.setNombre("Plan Mensual");
        request.setDescripcion("Acceso completo");
        request.setPrecio(new BigDecimal("50000"));
        request.setDuracionDias(30);
        request.setTipo(TipoPlan.MENSUAL);
        request.setIncluyeClases(true);
        request.setIncluyeEntrenadorPersonal(false);
    }

    @AfterEach
    void limpiarContextoSeguridad() {
        // Evita que el rol seteado en un test se filtre al siguiente
        // (SecurityContextHolder usa un ThreadLocal por defecto, pero los
        // tests de una misma clase corren en el mismo hilo).
        SecurityContextHolder.clearContext();
    }

    private void autenticarComo(String rol) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "test@gymflow.com", null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
    }

    private Plan planInactivo() {
        // Plan no tiene @Builder(toBuilder = true), asi que se construye
        // aparte en vez de derivar de `plan` con un builder parcial.
        return Plan.builder()
                .id(1L)
                .nombre("Plan Mensual")
                .descripcion("Acceso completo")
                .precio(new BigDecimal("50000"))
                .duracionDias(30)
                .tipo(TipoPlan.MENSUAL)
                .incluyeClases(true)
                .incluyeEntrenadorPersonal(false)
                .activo(false)
                .build();
    }

    @Test
    @SuppressWarnings("null")
    void crear_exitoso() {
        when(planRepository.existsByNombre("Plan Mensual")).thenReturn(false);

        PlanResponseDTO response = planService.crear(request);

        assertThat(response.getNombre()).isEqualTo("Plan Mensual");
        assertThat(response.getPrecio()).isEqualByComparingTo("50000");
        verify(planRepository).save(any());
    }

    @Test
    void crear_nombreDuplicado_lanzaExcepcion() {
        when(planRepository.existsByNombre("Plan Mensual")).thenReturn(true);

        assertThatThrownBy(() -> planService.crear(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Ya existe un plan con ese nombre");
    }

    @Test
    void listar_adminSinFiltro_retornaTodos() {
        // Solo ADMIN puede pasar activo=null y recibir todo (activos e
        // inactivos) — un CLIENTE con activo=null cae forzado a activo=true
        // (ver listar_clienteSinFiltro_soloDevuelveActivos).
        autenticarComo("ADMIN");
        Pageable pageable = PageRequest.of(0, 20);
        when(planRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(plan)));

        Page<PlanResponseDTO> resultado = planService.listar(null, pageable);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().get(0).getNombre()).isEqualTo("Plan Mensual");
        verify(planRepository).findAll(pageable);
    }

    @Test
    void listar_filtroActivo_retornaActivos() {
        autenticarComo("CLIENTE");
        Pageable pageable = PageRequest.of(0, 20);
        when(planRepository.findByActivo(true, pageable)).thenReturn(new PageImpl<>(List.of(plan)));

        Page<PlanResponseDTO> resultado = planService.listar(true, pageable);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().get(0).isActivo()).isTrue();
    }

    @Test
    void listar_clienteSinFiltro_soloDevuelveActivos() {
        // §8 (security-deep-dive-additional-findings.md): un CLIENTE nunca
        // debe poder ver planes inactivos, ni pasando activo=null ni
        // activo=false explicito — el service fuerza activo=true.
        autenticarComo("CLIENTE");
        Pageable pageable = PageRequest.of(0, 20);
        when(planRepository.findByActivo(true, pageable)).thenReturn(new PageImpl<>(List.of(plan)));

        Page<PlanResponseDTO> resultado = planService.listar(null, pageable);

        assertThat(resultado.getContent()).hasSize(1);
        verify(planRepository).findByActivo(true, pageable);
        verify(planRepository, never()).findAll(pageable);
    }

    @Test
    void listar_clienteConFiltroFalse_igualSoloDevuelveActivos() {
        autenticarComo("CLIENTE");
        Pageable pageable = PageRequest.of(0, 20);
        when(planRepository.findByActivo(true, pageable)).thenReturn(new PageImpl<>(List.of(plan)));

        planService.listar(false, pageable);

        // El CLIENTE no puede forzar ver inactivos ni pasando activo=false:
        // el service ignora el parametro y usa true igual.
        verify(planRepository).findByActivo(true, pageable);
        verify(planRepository, never()).findByActivo(eq(false), any());
    }

    @Test
    void listar_adminConFiltroFalse_devuelveInactivos() {
        Plan planInactivo = planInactivo();
        autenticarComo("ADMIN");
        Pageable pageable = PageRequest.of(0, 20);
        when(planRepository.findByActivo(false, pageable)).thenReturn(new PageImpl<>(List.of(planInactivo)));

        Page<PlanResponseDTO> resultado = planService.listar(false, pageable);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().get(0).isActivo()).isFalse();
    }

    @Test
    @SuppressWarnings("null")
    void obtenerPorId_clientePlanInactivo_lanzaExcepcionComoNoEncontrado() {
        Plan planInactivo = planInactivo();
        autenticarComo("CLIENTE");
        when(planRepository.findById(1L)).thenReturn(Optional.of(planInactivo));

        assertThatThrownBy(() -> planService.obtenerPorId(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Plan no encontrado");
    }

    @Test
    @SuppressWarnings("null")
    void obtenerPorId_adminPlanInactivo_loDevuelveNormal() {
        Plan planInactivo = planInactivo();
        autenticarComo("ADMIN");
        when(planRepository.findById(1L)).thenReturn(Optional.of(planInactivo));

        PlanResponseDTO resultado = planService.obtenerPorId(1L);

        assertThat(resultado.isActivo()).isFalse();
        assertThat(resultado.getId()).isEqualTo(1L);
    }

    @Test
    @SuppressWarnings("null")
    void obtenerPorId_clientePlanActivo_loDevuelveNormal() {
        autenticarComo("CLIENTE");
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));

        PlanResponseDTO resultado = planService.obtenerPorId(1L);

        assertThat(resultado.isActivo()).isTrue();
    }

    @Test
    @SuppressWarnings("null")
    void cambiarEstado_desactiva_exitoso() {
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));

        PlanResponseDTO resultado = planService.cambiarEstado(1L, false);

        assertThat(resultado.isActivo()).isFalse();
        verify(planRepository).save(plan);
    }

    @Test
    @SuppressWarnings("null")
    void cambiarEstado_planNoEncontrado_lanzaExcepcion() {
        when(planRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> planService.cambiarEstado(99L, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Plan no encontrado");
    }
}