package com.gymflow.backend.service;

import com.gymflow.backend.dto.ClienteElegibleDTO;
import com.gymflow.backend.model.AsignacionEntrenador;
import com.gymflow.backend.model.Plan;
import com.gymflow.backend.model.Suscripcion;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.EstadoSuscripcion;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.model.enums.TipoPlan;
import com.gymflow.backend.repository.AsignacionEntrenadorRepository;
import com.gymflow.backend.repository.SuscripcionRepository;
import com.gymflow.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntrenadorServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private SuscripcionRepository suscripcionRepository;
    @Mock
    private AsignacionEntrenadorRepository asignacionEntrenadorRepository;

    @InjectMocks
    private EntrenadorService entrenadorService;

    private Usuario entrenador;
    private Usuario clienteElegible;
    private Plan planConAcompañamiento;

    @BeforeEach
    void setUp() {
        entrenador = Usuario.builder().id(1L).nombre("Coach Ana").email("ana@gymflow.test")
                .rol(Rol.ENTRENADOR).activo(true).build();
        clienteElegible = Usuario.builder().id(2L).nombre("Cliente Beto").email("beto@gymflow.test")
                .rol(Rol.CLIENTE).activo(true).build();
        planConAcompañamiento = Plan.builder()
                .id(1L)
                .nombre("Plan Premium")
                .precio(new BigDecimal("90000"))
                .duracionDias(30)
                .tipo(TipoPlan.MENSUAL)
                .activo(true)
                .incluyeEntrenadorPersonal(true)
                .build();
    }

    @Test
    void listarClientesElegibles_soloClientesConPlanQueIncluyeAcompañamiento() {
        Usuario sinPlan = Usuario.builder().id(3L).nombre("Cliente Sin Plan").rol(Rol.CLIENTE).activo(true).build();
        when(usuarioRepository.findByEmail("ana@gymflow.test")).thenReturn(Optional.of(entrenador));
        when(asignacionEntrenadorRepository.findByEntrenadorIdAndActivaTrueOrderByAsignadoEnDesc(1L))
                .thenReturn(List.of());
        when(usuarioRepository.findByRolAndActivo(Rol.CLIENTE, true))
                .thenReturn(List.of(clienteElegible, sinPlan));
        when(suscripcionRepository.findByEstadoAndUsuarioIdIn(EstadoSuscripcion.ACTIVA, List.of(2L, 3L)))
                .thenReturn(List.of(Suscripcion.builder()
                        .usuario(clienteElegible).plan(planConAcompañamiento)
                        .fechaInicio(LocalDate.now()).estado(EstadoSuscripcion.ACTIVA).build()));

        List<ClienteElegibleDTO> elegibles = entrenadorService.listarClientesElegibles("ana@gymflow.test");

        assertThat(elegibles).hasSize(1);
        assertThat(elegibles.getFirst().nombre()).isEqualTo("Cliente Beto");
        assertThat(elegibles.getFirst().yaAcompaño()).isFalse();
        // Una sola query por lotes de suscripciones: sin N+1 por cliente.
        verify(suscripcionRepository, never()).findByUsuarioIdAndEstado(anyLong(), any());
    }

    @Test
    void listarClientesElegibles_marcaYaAcompaño() {
        AsignacionEntrenador asignacion = AsignacionEntrenador.builder()
                .id(5L).cliente(clienteElegible).entrenador(entrenador).build();
        when(usuarioRepository.findByEmail("ana@gymflow.test")).thenReturn(Optional.of(entrenador));
        when(asignacionEntrenadorRepository.findByEntrenadorIdAndActivaTrueOrderByAsignadoEnDesc(1L))
                .thenReturn(List.of(asignacion));
        when(usuarioRepository.findByRolAndActivo(Rol.CLIENTE, true)).thenReturn(List.of(clienteElegible));
        when(suscripcionRepository.findByEstadoAndUsuarioIdIn(EstadoSuscripcion.ACTIVA, List.of(2L)))
                .thenReturn(List.of(Suscripcion.builder()
                        .usuario(clienteElegible).plan(planConAcompañamiento)
                        .fechaInicio(LocalDate.now()).estado(EstadoSuscripcion.ACTIVA).build()));

        List<ClienteElegibleDTO> elegibles = entrenadorService.listarClientesElegibles("ana@gymflow.test");

        assertThat(elegibles.getFirst().yaAcompaño()).isTrue();
        assertThat(elegibles.getFirst().asignacionId()).isEqualTo(5L);
    }

    @Test
    void asignarme_noPuedeSerPropioAcompañante() {
        when(usuarioRepository.findByEmail("ana@gymflow.test")).thenReturn(Optional.of(entrenador));

        assertThatThrownBy(() -> entrenadorService.asignarme("ana@gymflow.test", 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no puedes ser tu propio acompañante");

        verify(asignacionEntrenadorRepository, never()).save(any());
    }

    @Test
    void asignarme_planSinEntrenadorPersonal_lanzaExcepcion() {
        when(usuarioRepository.findByEmail("ana@gymflow.test")).thenReturn(Optional.of(entrenador));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(clienteElegible));
        when(suscripcionRepository.findByUsuarioIdAndEstado(2L, EstadoSuscripcion.ACTIVA))
                .thenReturn(Optional.of(Suscripcion.builder()
                        .usuario(clienteElegible).plan(Plan.builder().id(2L).activo(true).incluyeEntrenadorPersonal(false).build())
                        .fechaInicio(LocalDate.now()).estado(EstadoSuscripcion.ACTIVA).build()));

        assertThatThrownBy(() -> entrenadorService.asignarme("ana@gymflow.test", 2L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no incluye entrenador personal");

        verify(asignacionEntrenadorRepository, never()).save(any());
    }

    @Test
    void asignarme_clienteYaAcompañado_lanzaExcepcion() {
        when(usuarioRepository.findByEmail("ana@gymflow.test")).thenReturn(Optional.of(entrenador));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(clienteElegible));
        when(suscripcionRepository.findByUsuarioIdAndEstado(2L, EstadoSuscripcion.ACTIVA))
                .thenReturn(Optional.of(Suscripcion.builder()
                        .usuario(clienteElegible).plan(planConAcompañamiento)
                        .fechaInicio(LocalDate.now()).estado(EstadoSuscripcion.ACTIVA).build()));
        when(asignacionEntrenadorRepository.findByClienteIdAndActivaTrue(2L))
                .thenReturn(Optional.of(AsignacionEntrenador.builder()
                        .id(5L).cliente(clienteElegible).entrenador(entrenador).build()));

        assertThatThrownBy(() -> entrenadorService.asignarme("ana@gymflow.test", 2L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ya tiene una asignación activa");

        verify(asignacionEntrenadorRepository, never()).save(any());
    }

    @Test
    void asignarme_ok_persisteAsignacion() {
        when(usuarioRepository.findByEmail("ana@gymflow.test")).thenReturn(Optional.of(entrenador));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(clienteElegible));
        when(suscripcionRepository.findByUsuarioIdAndEstado(2L, EstadoSuscripcion.ACTIVA))
                .thenReturn(Optional.of(Suscripcion.builder()
                        .usuario(clienteElegible).plan(planConAcompañamiento)
                        .fechaInicio(LocalDate.now()).estado(EstadoSuscripcion.ACTIVA).build()));
        when(asignacionEntrenadorRepository.findByClienteIdAndActivaTrue(2L)).thenReturn(Optional.empty());

        entrenadorService.asignarme("ana@gymflow.test", 2L);

        verify(asignacionEntrenadorRepository).save(any(AsignacionEntrenador.class));
    }

    @Test
    void cancelar_soloElEntrenadorDeLaAsignacionPuede() {
        when(usuarioRepository.findByEmail("ana@gymflow.test")).thenReturn(Optional.of(entrenador));
        when(asignacionEntrenadorRepository.findByIdAndEntrenadorId(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> entrenadorService.cancelar("ana@gymflow.test", 5L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("solo el entrenador de la asignación");

        verify(asignacionEntrenadorRepository, never()).save(any());
    }

    @Test
    void miHistorial_devuelveTodasLasAsignacionesDelCliente_masRecientePrimero() {
        Usuario beto = Usuario.builder().id(2L).nombre("Cliente Beto").email("beto@gymflow.test")
                .rol(Rol.CLIENTE).activo(true).build();
        Usuario otroEntrenador = Usuario.builder().id(9L).nombre("Coach Leo").rol(Rol.ENTRENADOR).activo(true).build();
        AsignacionEntrenador activa = AsignacionEntrenador.builder()
                .id(7L).cliente(beto).entrenador(entrenador).activa(true).build();
        AsignacionEntrenador cancelada = AsignacionEntrenador.builder()
                .id(6L).cliente(beto).entrenador(otroEntrenador).activa(false).build();
        when(usuarioRepository.findByEmail("beto@gymflow.test")).thenReturn(Optional.of(beto));
        when(asignacionEntrenadorRepository.findByClienteIdOrderByAsignadoEnDesc(2L))
                .thenReturn(List.of(activa, cancelada));

        var historial = entrenadorService.miHistorial("beto@gymflow.test");

        assertThat(historial).hasSize(2);
        assertThat(historial.getFirst().entrenadorNombre()).isEqualTo("Coach Ana");
        assertThat(historial.getFirst().activa()).isTrue();
        assertThat(historial.get(1).entrenadorNombre()).isEqualTo("Coach Leo");
        assertThat(historial.get(1).activa()).isFalse();
    }
}
