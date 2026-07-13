package com.gymflow.backend.service;

import com.gymflow.backend.dto.PlanRequestDTO;
import com.gymflow.backend.dto.PlanResponseDTO;
import com.gymflow.backend.model.Plan;
import com.gymflow.backend.model.enums.TipoPlan;
import com.gymflow.backend.repository.PlanRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
    void listar_sinFiltro_retornaTodos() {
        Pageable pageable = PageRequest.of(0, 20);
        when(planRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(plan)));

        Page<PlanResponseDTO> resultado = planService.listar(null, pageable);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().get(0).getNombre()).isEqualTo("Plan Mensual");
    }

    @Test
    void listar_filtroActivo_retornaActivos() {
        Pageable pageable = PageRequest.of(0, 20);
        when(planRepository.findByActivo(true, pageable)).thenReturn(new PageImpl<>(List.of(plan)));

        Page<PlanResponseDTO> resultado = planService.listar(true, pageable);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().get(0).isActivo()).isTrue();
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