package com.gymflow.backend.service;

import com.gymflow.backend.dto.AsistenciaAcompanadoDTO;
import com.gymflow.backend.dto.AsistenciaResponseDTO;
import com.gymflow.backend.dto.AsistenciaSemanaDTO;
import com.gymflow.backend.dto.CarnetResponseDTO;
import com.gymflow.backend.exception.KioskKeyInvalidaException;
import com.gymflow.backend.model.AsignacionEntrenador;
import com.gymflow.backend.model.Asistencia;
import com.gymflow.backend.model.Plan;
import com.gymflow.backend.model.Suscripcion;
import com.gymflow.backend.model.Usuario;
import com.gymflow.backend.model.enums.EstadoSuscripcion;
import com.gymflow.backend.model.enums.MetodoAsistencia;
import com.gymflow.backend.model.enums.Rol;
import com.gymflow.backend.repository.AsignacionEntrenadorRepository;
import com.gymflow.backend.repository.AsistenciaRepository;
import com.gymflow.backend.repository.SuscripcionRepository;
import com.gymflow.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsistenciaServiceTest {

    // 2026-08-03 es lunes. 15:00 UTC = 10:00 en la zona Bogotá.
    private static final Instant INSTANTE = Instant.parse("2026-08-03T15:00:00Z");
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalDate HOY = LocalDate.of(2026, 8, 3);
    private static final LocalDateTime HOY_A_LAS_10 = LocalDateTime.of(2026, 8, 3, 10, 0);
    private static final String EMAIL = "ana@gymflow.test";

    @Mock
    private AsistenciaRepository asistenciaRepository;
    @Mock
    private SuscripcionRepository suscripcionRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private KioscoConfigService kioscoConfigService;
    @Mock
    private AsignacionEntrenadorRepository asignacionEntrenadorRepository;

    private AsistenciaService servicio;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        // Clock.fixed es valor, no mock: construimos el service a mano (mismo
        // criterio que SuscripcionServiceTest tras el fix de TZ).
        Clock clock = Clock.fixed(INSTANTE, BOGOTA);
        servicio = new AsistenciaService(asistenciaRepository, suscripcionRepository,
                usuarioRepository, kioscoConfigService, asignacionEntrenadorRepository, clock);
        usuario = Usuario.builder()
                .id(1L)
                .nombre("Ana")
                .email(EMAIL)
                .rol(Rol.CLIENTE)
                .activo(true)
                .build();
        // lenient: los tests del kiosco resuelven por findByCodigoCarnet, no
        // por email, y ese stub no debería contarlas como "innecesario".
        lenient().when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        // lenient: adminMarcar resuelve por findById (id del body); los tests
        // del 404 lo sobreescriben con Optional.empty().
        lenient().when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
    }

    private Suscripcion suscripcionActiva(LocalDate fechaFin) {
        return suscripcionActiva(fechaFin, true);
    }

    private Suscripcion suscripcionActiva(LocalDate fechaFin, boolean planActivo) {
        return Suscripcion.builder()
                .usuario(usuario)
                .plan(Plan.builder().id(10L).activo(planActivo).build())
                .fechaInicio(HOY.minusDays(10))
                .fechaFin(fechaFin)
                .estado(EstadoSuscripcion.ACTIVA)
                .build();
    }

    private void conSuscripcionActiva(LocalDate fechaFin) {
        conSuscripcionActiva(fechaFin, true);
    }

    private void conSuscripcionActiva(LocalDate fechaFin, boolean planActivo) {
        when(suscripcionRepository.findByUsuarioIdAndEstado(1L, EstadoSuscripcion.ACTIVA))
                .thenReturn(Optional.of(suscripcionActiva(fechaFin, planActivo)));
    }

    @Test
    void marcarMi_registraConMetodoSELFYFechasDelClock() {
        conSuscripcionActiva(HOY.plusDays(20));
        when(asistenciaRepository.existsByUsuarioIdAndFecha(1L, HOY)).thenReturn(false);
        when(asistenciaRepository.save(any(Asistencia.class))).thenReturn(Asistencia.builder()
                .id(7L)
                .usuario(usuario)
                .fecha(HOY)
                .entradaEn(HOY_A_LAS_10)
                .metodo(MetodoAsistencia.SELF)
                .build());

        AsistenciaResponseDTO dto = servicio.marcarMi(EMAIL);

        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(dto.getNombre()).isEqualTo("Ana");
        assertThat(dto.getFecha()).isEqualTo(HOY);
        assertThat(dto.getEntradaEn()).isEqualTo(HOY_A_LAS_10);
        assertThat(dto.getMetodo()).isEqualTo(MetodoAsistencia.SELF);
        verify(asistenciaRepository).save(argThat(a ->
                a.getMetodo() == MetodoAsistencia.SELF
                        && a.getFecha().equals(HOY)
                        && a.getEntradaEn().equals(HOY_A_LAS_10)));
    }

    @Test
    void marcarMi_usuarioInactivo_lanza403() {
        usuario.setActivo(false);

        assertThatThrownBy(() -> servicio.marcarMi(EMAIL))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("El usuario está dado de baja");
        verifyNoInteractions(suscripcionRepository, asistenciaRepository);
    }

    @Test
    void marcarMi_sinSuscripcionActiva_lanza400() {
        when(suscripcionRepository.findByUsuarioIdAndEstado(1L, EstadoSuscripcion.ACTIVA))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.marcarMi(EMAIL))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("No tenés un plan activo para registrar tu entrada");
        verify(asistenciaRepository, never()).existsByUsuarioIdAndFecha(any(), any());
    }

    @Test
    void marcarMi_suscripcionActivaVencida_lanza400() {
        conSuscripcionActiva(HOY.minusDays(1));

        assertThatThrownBy(() -> servicio.marcarMi(EMAIL))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("No tenés un plan activo para registrar tu entrada");
    }

    @Test
    void marcarMi_planInactivo_lanza400() {
        conSuscripcionActiva(HOY.plusDays(20), false);

        assertThatThrownBy(() -> servicio.marcarMi(EMAIL))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("No tenés un plan activo para registrar tu entrada");
    }

    @Test
    void marcarMi_duplicadoPorExists_lanza409() {
        conSuscripcionActiva(HOY.plusDays(20));
        when(asistenciaRepository.existsByUsuarioIdAndFecha(1L, HOY)).thenReturn(true);

        assertThatThrownBy(() -> servicio.marcarMi(EMAIL))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Ya registraste tu entrada hoy");
        verify(asistenciaRepository, never()).save(any());
    }

    @Test
    void marcarMi_duplicadoPorCarrera_lanza409() {
        conSuscripcionActiva(HOY.plusDays(20));
        when(asistenciaRepository.existsByUsuarioIdAndFecha(1L, HOY)).thenReturn(false);
        when(asistenciaRepository.save(any(Asistencia.class)))
                .thenThrow(new DataIntegrityViolationException("duplicado p/ uq_asistencia_por_dia"));

        assertThatThrownBy(() -> servicio.marcarMi(EMAIL))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Ya registraste tu entrada hoy");
    }

    @Test
    void marcarMi_usuarioInexistente_lanza404() {
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.marcarMi(EMAIL))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Usuario no encontrado con email: " + EMAIL);
        verifyNoInteractions(suscripcionRepository, asistenciaRepository);
    }

    @Test
    void miCarnet_devuelveElCodigoDelClienteSinDatosDeOtroUsuario() {
        usuario.setCodigoCarnet("ABC123");

        CarnetResponseDTO dto = servicio.miCarnet(EMAIL);

        assertThat(dto.getCodigoCarnet()).isEqualTo("ABC123");
        assertThat(dto.getNombre()).isNull();
        verifyNoInteractions(asistenciaRepository, suscripcionRepository);
    }

    @Test
    void miCarnet_usuarioSinCodigo_lanza404() {
        usuario.setCodigoCarnet(null);

        assertThatThrownBy(() -> servicio.miCarnet(EMAIL))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Código de carnet no encontrado");
    }

    @Test
    void miCarnet_usuarioInexistente_lanza404() {
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.miCarnet(EMAIL))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Usuario no encontrado con email: " + EMAIL);
    }

    @Test
    void semana_devuelveLunesADomingoTotallyOrdenAscendente() {
        Asistencia martes = Asistencia.builder()
                .id(2L).usuario(usuario).fecha(LocalDate.of(2026, 8, 4))
                .entradaEn(LocalDateTime.of(2026, 8, 4, 9, 0))
                .metodo(MetodoAsistencia.SELF).build();
        Asistencia lunes = Asistencia.builder()
                .id(1L).usuario(usuario).fecha(HOY)
                .entradaEn(HOY_A_LAS_10)
                .metodo(MetodoAsistencia.SELF).build();
        when(asistenciaRepository.findByUsuarioIdAndFechaBetween(1L, HOY, LocalDate.of(2026, 8, 9)))
                .thenReturn(List.of(martes, lunes));

        AsistenciaSemanaDTO dto = servicio.semana(EMAIL);

        assertThat(dto.getFechaDesde()).isEqualTo(HOY);
        assertThat(dto.getFechaHasta()).isEqualTo(LocalDate.of(2026, 8, 9));
        assertThat(dto.getTotal()).isEqualTo(2);
        assertThat(dto.getAsistencias())
                .extracting(AsistenciaResponseDTO::getFecha)
                .containsExactly(HOY, LocalDate.of(2026, 8, 4));
        assertThat(dto.getAsistencias())
                .extracting(AsistenciaResponseDTO::getNombre)
                .containsOnly("Ana");
        assertThat(dto.getAsistencias()).allMatch(a -> a.getMetodo() == MetodoAsistencia.SELF);
    }

    // ---- Fase 5, P4: kiosco de recepción (POST /api/asistencias/kiosk) ----

    private static final String CODIGO_CARNET = "ABC123";
    private static final String KEY_KIOSCO = "key-valida-del-kiosco";

    private void conKioscoYClienteValidos() {
        usuario.setCodigoCarnet(CODIGO_CARNET);
        when(kioscoConfigService.validar(KEY_KIOSCO)).thenReturn(true);
        when(usuarioRepository.findByCodigoCarnet(CODIGO_CARNET)).thenReturn(Optional.of(usuario));
    }

    @Test
    void marcarKiosk_registraConMetodoKIOSK_CARNET() {
        conKioscoYClienteValidos();
        conSuscripcionActiva(HOY.plusDays(20));
        when(asistenciaRepository.existsByUsuarioIdAndFecha(1L, HOY)).thenReturn(false);
        when(asistenciaRepository.save(any(Asistencia.class))).thenReturn(Asistencia.builder()
                .id(9L)
                .usuario(usuario)
                .fecha(HOY)
                .entradaEn(HOY_A_LAS_10)
                .metodo(MetodoAsistencia.KIOSK_CARNET)
                .build());

        AsistenciaResponseDTO dto = servicio.marcarKiosk(CODIGO_CARNET, KEY_KIOSCO);

        assertThat(dto.getId()).isEqualTo(9L);
        assertThat(dto.getNombre()).isEqualTo("Ana");
        assertThat(dto.getMetodo()).isEqualTo(MetodoAsistencia.KIOSK_CARNET);
        verify(asistenciaRepository).save(argThat(a -> a.getMetodo() == MetodoAsistencia.KIOSK_CARNET));
    }

    @Test
    void marcarKiosk_keyInvalidaOAusente_lanza401() {
        when(kioscoConfigService.validar(KEY_KIOSCO)).thenReturn(false);

        assertThatThrownBy(() -> servicio.marcarKiosk(CODIGO_CARNET, KEY_KIOSCO))
                .isInstanceOf(KioskKeyInvalidaException.class);
        verifyNoInteractions(usuarioRepository, suscripcionRepository, asistenciaRepository);
    }

    @Test
    void marcarKiosk_codigoInexistente_lanza400Generico() {
        // 400 genérico (no 404, no revela el código): anti-enumeración, spec #2
        usuario.setCodigoCarnet(CODIGO_CARNET);
        when(kioscoConfigService.validar(KEY_KIOSCO)).thenReturn(true);
        when(usuarioRepository.findByCodigoCarnet(CODIGO_CARNET)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.marcarKiosk(CODIGO_CARNET, KEY_KIOSCO))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Código de carnet inválido para el kiosco de recepción");
        verifyNoInteractions(suscripcionRepository, asistenciaRepository);
    }

    @Test
    void marcarKiosk_usuarioInactivo_lanza403() {
        conKioscoYClienteValidos();
        usuario.setActivo(false);

        assertThatThrownBy(() -> servicio.marcarKiosk(CODIGO_CARNET, KEY_KIOSCO))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("El usuario está dado de baja");
        verifyNoInteractions(suscripcionRepository, asistenciaRepository);
    }

    @Test
    void marcarKiosk_sinPlanActivo_lanza400() {
        conKioscoYClienteValidos();
        when(suscripcionRepository.findByUsuarioIdAndEstado(1L, EstadoSuscripcion.ACTIVA))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.marcarKiosk(CODIGO_CARNET, KEY_KIOSCO))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("No tenés un plan activo para registrar tu entrada");
        verify(asistenciaRepository, never()).existsByUsuarioIdAndFecha(any(), any());
    }

    @Test
    void marcarKiosk_yaRegistroHoy_lanza409() {
        conKioscoYClienteValidos();
        conSuscripcionActiva(HOY.plusDays(20));
        when(asistenciaRepository.existsByUsuarioIdAndFecha(1L, HOY)).thenReturn(true);

        assertThatThrownBy(() -> servicio.marcarKiosk(CODIGO_CARNET, KEY_KIOSCO))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Ya registraste tu entrada hoy");
        verify(asistenciaRepository, never()).save(any());
    }

    @Test
    void marcarKiosk_carreraDeDuplicado_lanza409() {
        conKioscoYClienteValidos();
        conSuscripcionActiva(HOY.plusDays(20));
        when(asistenciaRepository.existsByUsuarioIdAndFecha(1L, HOY)).thenReturn(false);
        when(asistenciaRepository.save(any(Asistencia.class)))
                .thenThrow(new DataIntegrityViolationException("duplicado p/ uq_asistencia_por_dia"));

        assertThatThrownBy(() -> servicio.marcarKiosk(CODIGO_CARNET, KEY_KIOSCO))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Ya registraste tu entrada hoy");
    }

    // ---- Fase 5, P5: semana del ENTRENADOR + control del ADMIN ----

    private Usuario cliente(long id, String nombre) {
        return Usuario.builder().id(id).nombre(nombre).rol(Rol.CLIENTE).activo(true).build();
    }

    private Asistencia asistencia(long id, Usuario usuario, int dia, MetodoAsistencia metodo) {
        return Asistencia.builder()
                .id(id)
                .usuario(usuario)
                .fecha(LocalDate.of(2026, 8, dia))
                .entradaEn(LocalDateTime.of(2026, 8, dia, 9, 0))
                .metodo(metodo)
                .build();
    }

    @Test
    void semanaAcompanados_agrupaPorClienteConBatchSinN1() {
        Usuario ana = usuario; // id 1
        Usuario beto = cliente(2L, "Beto");
        when(asignacionEntrenadorRepository.findByEntrenadorIdAndActivaTrueOrderByAsignadoEnDesc(1L))
                .thenReturn(List.of(
                        AsignacionEntrenador.builder().cliente(ana).activa(true).build(),
                        AsignacionEntrenador.builder().cliente(beto).activa(true).build()));
        // Ana marcó lunes y martes; Beto no marcó nada esta semana.
        when(asistenciaRepository.findByUsuarioIdInAndFechaBetween(
                List.of(1L, 2L), HOY, LocalDate.of(2026, 8, 9)))
                .thenReturn(List.of(
                        asistencia(1L, ana, 3, MetodoAsistencia.SELF),
                        asistencia(2L, ana, 4, MetodoAsistencia.KIOSK_CARNET)));

        List<AsistenciaAcompanadoDTO> resultado = servicio.semanaAcompanados(EMAIL);

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).getClienteNombre()).isEqualTo("Ana");
        assertThat(resultado.get(0).getAsistencias()).extracting(AsistenciaResponseDTO::getId)
                .containsExactly(1L, 2L);
        // El cliente sin asistencias entra con lista vacía (no "no acompañado").
        assertThat(resultado.get(1).getClienteId()).isEqualTo(2L);
        assertThat(resultado.get(1).getAsistencias()).isEmpty();
        verify(asistenciaRepository).findByUsuarioIdInAndFechaBetween(
                List.of(1L, 2L), HOY, LocalDate.of(2026, 8, 9));
        verify(asistenciaRepository, never()).findByUsuarioIdAndFechaBetween(any(), any(), any());
    }

    @Test
    void semanaAcompanados_sinAsignacionesActivas_devuelveListaVacia() {
        when(asignacionEntrenadorRepository.findByEntrenadorIdAndActivaTrueOrderByAsignadoEnDesc(1L))
                .thenReturn(List.of());

        List<AsistenciaAcompanadoDTO> resultado = servicio.semanaAcompanados(EMAIL);

        assertThat(resultado).isEmpty();
        verifyNoInteractions(asistenciaRepository);
    }

    @Test
    void adminMarcar_registraConMetodoADMIN() {
        conSuscripcionActiva(HOY.plusDays(20));
        when(asistenciaRepository.existsByUsuarioIdAndFecha(1L, HOY)).thenReturn(false);
        when(asistenciaRepository.save(any(Asistencia.class))).thenReturn(Asistencia.builder()
                .id(8L)
                .usuario(usuario)
                .fecha(HOY)
                .entradaEn(HOY_A_LAS_10)
                .metodo(MetodoAsistencia.ADMIN)
                .build());

        AsistenciaResponseDTO dto = servicio.adminMarcar(1L);

        assertThat(dto.getId()).isEqualTo(8L);
        assertThat(dto.getMetodo()).isEqualTo(MetodoAsistencia.ADMIN);
        verify(asistenciaRepository).save(argThat(a -> a.getMetodo() == MetodoAsistencia.ADMIN));
    }

    @Test
    void adminMarcar_usuarioInexistente_lanza404() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.adminMarcar(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Usuario no encontrado con id: 1");
        verifyNoInteractions(suscripcionRepository, asistenciaRepository);
    }

    @Test
    void adminMarcar_usuarioInactivo_lanza403() {
        usuario.setActivo(false);

        assertThatThrownBy(() -> servicio.adminMarcar(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("El usuario está dado de baja");
        verifyNoInteractions(suscripcionRepository, asistenciaRepository);
    }

    @Test
    void adminMarcar_sinPlanActivo_lanza400() {
        when(suscripcionRepository.findByUsuarioIdAndEstado(1L, EstadoSuscripcion.ACTIVA))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.adminMarcar(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("No tenés un plan activo para registrar tu entrada");
    }

    @Test
    void adminMarcar_duplicado_lanza409ConMensajeDistintoDelSelf() {
        conSuscripcionActiva(HOY.plusDays(20));
        when(asistenciaRepository.existsByUsuarioIdAndFecha(1L, HOY)).thenReturn(true);

        assertThatThrownBy(() -> servicio.adminMarcar(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("El cliente ya registró su entrada hoy");
        verify(asistenciaRepository, never()).save(any());
    }

    @Test
    void adminMarcar_carreraDeDuplicado_lanza409() {
        conSuscripcionActiva(HOY.plusDays(20));
        when(asistenciaRepository.existsByUsuarioIdAndFecha(1L, HOY)).thenReturn(false);
        when(asistenciaRepository.save(any(Asistencia.class)))
                .thenThrow(new DataIntegrityViolationException("duplicado p/ uq_asistencia_por_dia"));

        assertThatThrownBy(() -> servicio.adminMarcar(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("El cliente ya registró su entrada hoy");
    }

    @Test
    void desmarcar_laDeHoy_elimina() {
        when(asistenciaRepository.findById(7L)).thenReturn(Optional.of(asistencia(7L, usuario, 3, MetodoAsistencia.SELF)));

        servicio.desmarcar(7L);

        verify(asistenciaRepository).delete(argThat(a -> a.getId().equals(7L)));
    }

    @Test
    void desmarcar_laDeOtroDia_lanza400() {
        when(asistenciaRepository.findById(7L))
                .thenReturn(Optional.of(asistencia(7L, usuario, 1, MetodoAsistencia.ADMIN)));

        assertThatThrownBy(() -> servicio.desmarcar(7L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("No se puede desmarcar una asistencia de otro día");
        verify(asistenciaRepository, never()).delete(any());
    }

    @Test
    void desmarcar_inexistente_lanza404() {
        when(asistenciaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.desmarcar(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Asistencia no encontrada con id: 99");
        verify(asistenciaRepository, never()).delete(any());
    }

    @Test
    void historial_devuelveLaPaginaMapeada() {
        Asistencia a = asistencia(3L, usuario, 3, MetodoAsistencia.SELF);
        Page<Asistencia> pagina = new PageImpl<>(List.of(a), PageRequest.of(0, 20), 1);
        when(asistenciaRepository.findByUsuarioId(1L, PageRequest.of(0, 20))).thenReturn(pagina);

        Page<AsistenciaResponseDTO> resultado = servicio.historial(1L, PageRequest.of(0, 20));

        assertThat(resultado.getTotalElements()).isEqualTo(1);
        assertThat(resultado.getContent().get(0).getMetodo()).isEqualTo(MetodoAsistencia.SELF);
        assertThat(resultado.getContent().get(0).getNombre()).isEqualTo("Ana");
    }
}