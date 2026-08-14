package com.gymflow.backend.service;

import com.gymflow.backend.dto.ClienteAsignadoDTO;
import com.gymflow.backend.dto.EjercicioRequestDTO;
import com.gymflow.backend.dto.RutinaRequestDTO;
import com.gymflow.backend.dto.RutinaResponseDTO;
import com.gymflow.backend.model.AsignacionEntrenador;
import com.gymflow.backend.model.AsignacionRutina;
import com.gymflow.backend.model.Ejercicio;
import com.gymflow.backend.model.Rutina;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.repository.AsignacionEntrenadorRepository;
import com.gymflow.backend.repository.AsignacionRutinaRepository;
import com.gymflow.backend.repository.RutinaRepository;
import com.gymflow.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RutinaServiceTest {

    @Mock
    private RutinaRepository rutinaRepository;
    @Mock
    private AsignacionRutinaRepository asignacionRutinaRepository;
    @Mock
    private AsignacionEntrenadorRepository asignacionEntrenadorRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private RutinaService rutinaService;

    private Usuario entrenador;
    private Usuario cliente;
    private Rutina rutina;
    private RutinaRequestDTO request;

    @BeforeEach
    void setUp() {
        entrenador = Usuario.builder().id(1L).nombre("Coach Ana").email("ana@gymflow.test")
                .rol(Rol.ENTRENADOR).activo(true).build();
        cliente = Usuario.builder().id(2L).nombre("Cliente Beto").email("beto@gymflow.test")
                .rol(Rol.CLIENTE).activo(true).build();
        rutina = Rutina.builder()
                .id(10L)
                .entrenador(entrenador)
                .nombre("Full Body")
                .descripcion("Arranque")
                .activo(true)
                .ejercicios(new ArrayList<>(List.of(
                        Ejercicio.builder().id(1L).nombre("Press banca").series(3).repeticiones(10).orden(1).build())))
                .build();
        request = new RutinaRequestDTO("Full Body", "Arranque",
                List.of(new EjercicioRequestDTO(null, "Press banca", 3, 10)));
    }

    @Test
    void listarMias_pueblaAsignadosPorRutina() {
        AsignacionRutina asignacion = AsignacionRutina.builder()
                .id(1L).cliente(cliente).rutina(rutina).build();
        when(usuarioRepository.findByEmail("ana@gymflow.test")).thenReturn(Optional.of(entrenador));
        when(rutinaRepository.findByEntrenadorIdOrderByCreadoEnDesc(1L)).thenReturn(List.of(rutina));
        when(asignacionRutinaRepository.findByRutinaEntrenadorId(1L)).thenReturn(List.of(asignacion));

        List<RutinaResponseDTO> rutinas = rutinaService.listarMias("ana@gymflow.test");

        assertThat(rutinas).hasSize(1);
        assertThat(rutinas.getFirst().asignados())
                .extracting(ClienteAsignadoDTO::id, ClienteAsignadoDTO::nombre)
                .containsExactly(tuple(2L, "Cliente Beto"));
    }

    @Test
    void crear_asignaElEntrenadorDesdeElEmailYDevuelveElDTO() {
        when(usuarioRepository.findByEmail("ana@gymflow.test")).thenReturn(Optional.of(entrenador));
        when(rutinaRepository.save(any(Rutina.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RutinaResponseDTO response = rutinaService.crear("ana@gymflow.test", request);

        assertThat(response.nombre()).isEqualTo("Full Body");
        assertThat(response.ejercicios()).hasSize(1);
        assertThat(response.ejercicios().getFirst().orden()).isEqualTo(1);
        verify(rutinaRepository).save(any(Rutina.class));
    }

    @Test
    void actualizar_rutinaDeOtroEntrenador_lanzaExcepcion() {
        when(usuarioRepository.findByEmail("ana@gymflow.test")).thenReturn(Optional.of(entrenador));
        when(rutinaRepository.findByIdAndEntrenadorId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rutinaService.actualizar("ana@gymflow.test", 10L, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("solo el entrenador creador");

        verify(rutinaRepository, never()).save(any());
    }

    @Test
    void asignar_rutinaInactiva_lanzaExcepcion() {
        when(usuarioRepository.findByEmail("ana@gymflow.test")).thenReturn(Optional.of(entrenador));
        when(rutinaRepository.findByIdAndEntrenadorId(10L, 1L)).thenReturn(Optional.of(rutina));

        rutina.setActivo(false);

        assertThatThrownBy(() -> rutinaService.asignar("ana@gymflow.test", 10L, 2L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("solo puedes asignar rutinas activas");

        verify(asignacionRutinaRepository, never()).save(any());
    }

    @Test
    void asignar_clienteNoAcompañado_lanzaExcepcion() {
        when(usuarioRepository.findByEmail("ana@gymflow.test")).thenReturn(Optional.of(entrenador));
        when(rutinaRepository.findByIdAndEntrenadorId(10L, 1L)).thenReturn(Optional.of(rutina));
        when(asignacionEntrenadorRepository.findByClienteIdAndActivaTrue(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rutinaService.asignar("ana@gymflow.test", 10L, 2L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("solo puedes asignar rutinas a tus clientes acompañados");

        verify(asignacionRutinaRepository, never()).save(any());
    }

    @Test
    void asignar_clienteYaTieneLaRutina_lanzaExcepcion() {
        when(usuarioRepository.findByEmail("ana@gymflow.test")).thenReturn(Optional.of(entrenador));
        when(rutinaRepository.findByIdAndEntrenadorId(10L, 1L)).thenReturn(Optional.of(rutina));
        when(asignacionEntrenadorRepository.findByClienteIdAndActivaTrue(2L))
                .thenReturn(Optional.of(AsignacionEntrenador.builder()
                        .id(1L).cliente(cliente).entrenador(entrenador).build()));
        when(asignacionRutinaRepository.existsByClienteIdAndRutinaId(2L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> rutinaService.asignar("ana@gymflow.test", 10L, 2L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("el cliente ya tiene esta rutina asignada");

        verify(asignacionRutinaRepository, never()).save(any());
    }

    @Test
    void asignar_ok_persisteAsignacion() {
        when(usuarioRepository.findByEmail("ana@gymflow.test")).thenReturn(Optional.of(entrenador));
        when(rutinaRepository.findByIdAndEntrenadorId(10L, 1L)).thenReturn(Optional.of(rutina));
        when(asignacionEntrenadorRepository.findByClienteIdAndActivaTrue(2L))
                .thenReturn(Optional.of(AsignacionEntrenador.builder()
                        .id(1L).cliente(cliente).entrenador(entrenador).build()));
        when(asignacionRutinaRepository.existsByClienteIdAndRutinaId(2L, 10L)).thenReturn(false);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(cliente));

        rutinaService.asignar("ana@gymflow.test", 10L, 2L);

        verify(asignacionRutinaRepository).save(any(AsignacionRutina.class));
    }

    @Test
    void quitar_esIdempotente_sinAsignacionNoFalla() {
        when(usuarioRepository.findByEmail("ana@gymflow.test")).thenReturn(Optional.of(entrenador));
        when(rutinaRepository.findByIdAndEntrenadorId(10L, 1L)).thenReturn(Optional.of(rutina));
        when(asignacionRutinaRepository.findByClienteIdAndRutinaId(2L, 10L)).thenReturn(Optional.empty());

        rutinaService.quitar("ana@gymflow.test", 10L, 2L);

        verify(asignacionRutinaRepository, never()).delete(any());
    }
}
