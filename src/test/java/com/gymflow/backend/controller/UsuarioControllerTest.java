package com.gymflow.backend.controller;

import com.gymflow.backend.dto.CarnetResponseDTO;
import com.gymflow.backend.dto.ResultadoImportacionDTO;
import com.gymflow.backend.dto.UsuarioResponseDTO;
import com.gymflow.backend.dto.request.CambioRolRequest;
import com.gymflow.backend.dto.request.CrearClienteRequest;
import com.gymflow.backend.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import com.gymflow.backend.model.enums.Rol;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioControllerTest {

    @Mock
    private UsuarioService usuarioService;

    @InjectMocks
    private UsuarioController usuarioController;

    @Test
    void cambiarRol_delegaRolYDevuelve200() {
        CambioRolRequest request = new CambioRolRequest();
        request.setRol(Rol.ENTRENADOR);
        UsuarioResponseDTO esperado = UsuarioResponseDTO.builder()
                .id(7L)
                .rol(Rol.ENTRENADOR)
                .build();
        when(usuarioService.cambiarRol(7L, Rol.ENTRENADOR)).thenReturn(esperado);

        ResponseEntity<UsuarioResponseDTO> respuesta = usuarioController.cambiarRol(
                7L, request);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).isSameAs(esperado);
        verify(usuarioService).cambiarRol(7L, Rol.ENTRENADOR);
    }

    @Test
    void cambiarRol_tienePreAuthorizeSoloAdmin() throws NoSuchMethodException {
        Method method = UsuarioController.class.getMethod(
                "cambiarRol", Long.class, CambioRolRequest.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void carnet_devuelve200_yDelegaEnElServicio() {
        CarnetResponseDTO esperado = CarnetResponseDTO.builder()
                .codigoCarnet("ABC123")
                .nombre("Ana")
                .build();
        when(usuarioService.obtenerCarnet(7L)).thenReturn(esperado);

        ResponseEntity<CarnetResponseDTO> respuesta = usuarioController.carnet(7L);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).isSameAs(esperado);
        verify(usuarioService).obtenerCarnet(7L);
    }

    @Test
    void carnet_tienePreAuthorizeSoloAdmin() throws NoSuchMethodException {
        Method method = UsuarioController.class.getMethod("carnet", Long.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void rotarCarnet_devuelve200_yDelegaEnElServicio() {
        CarnetResponseDTO esperado = CarnetResponseDTO.builder()
                .codigoCarnet("XYZ789")
                .build();
        when(usuarioService.rotarCarnet(7L)).thenReturn(esperado);

        ResponseEntity<CarnetResponseDTO> respuesta = usuarioController.rotarCarnet(7L);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).isSameAs(esperado);
        verify(usuarioService).rotarCarnet(7L);
    }

    @Test
    void rotarCarnet_tienePreAuthorizeSoloAdmin() throws NoSuchMethodException {
        Method method = UsuarioController.class.getMethod("rotarCarnet", Long.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void eliminar_devuelve204_yDelegaEnElServicio() {
        ResponseEntity<Void> respuesta = usuarioController.eliminar(7L);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(204);
        assertThat(respuesta.getBody()).isNull();
        verify(usuarioService).eliminar(7L);
    }

    @Test
    void eliminar_tienePreAuthorizeSoloAdmin() throws NoSuchMethodException {
        Method method = UsuarioController.class.getMethod("eliminar", Long.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void crear_devuelve201_yDelegaEnElServicio() {
        CrearClienteRequest request = new CrearClienteRequest();
        request.setNombre("Socio Nuevo");
        request.setEmail("socio@gymflow.com");
        request.setTipoDocumento("CC");
        request.setNumeroDocumento("123456");
        UsuarioResponseDTO esperado = UsuarioResponseDTO.builder()
                .id(9L)
                .rol(Rol.CLIENTE)
                .tipoDocumento("CC")
                .numeroDocumento("123456")
                .build();
        when(usuarioService.crearCliente(request)).thenReturn(esperado);

        ResponseEntity<UsuarioResponseDTO> respuesta = usuarioController.crear(request);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(201);
        assertThat(respuesta.getBody()).isSameAs(esperado);
        verify(usuarioService).crearCliente(request);
    }

    @Test
    void crear_tienePreAuthorizeSoloAdmin() throws NoSuchMethodException {
        Method method = UsuarioController.class.getMethod("crear", CrearClienteRequest.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void importar_devuelve200_yDelegaEnElServicio() throws Exception {
        MultipartFile archivo = mock(MultipartFile.class);
        ByteArrayInputStream contenido = new ByteArrayInputStream("csv".getBytes());
        when(archivo.isEmpty()).thenReturn(false);
        when(archivo.getInputStream()).thenReturn(contenido);
        ResultadoImportacionDTO esperado = new ResultadoImportacionDTO(1, 1, List.of(), List.of());
        when(usuarioService.importarSocios(contenido, true)).thenReturn(esperado);

        ResponseEntity<ResultadoImportacionDTO> respuesta =
                usuarioController.importar(archivo, true);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).isSameAs(esperado);
        verify(usuarioService).importarSocios(contenido, true);
    }

    @Test
    void importar_tienePreAuthorizeSoloAdmin() throws NoSuchMethodException {
        Method method = UsuarioController.class.getMethod(
                "importar", MultipartFile.class, boolean.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void plantillaImportacion_devuelveCsvConHeader() {
        ResponseEntity<String> respuesta = usuarioController.plantillaImportacion();

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).startsWith("nombre,tipoDocumento,numeroDocumento");
    }

    @Test
    void porDocumento_devuelve200_yDelegaEnElServicio() {
        UsuarioResponseDTO esperado = UsuarioResponseDTO.builder()
                .id(3L)
                .tipoDocumento("CC")
                .numeroDocumento("123")
                .build();
        when(usuarioService.buscarPorDocumento("CC", "123")).thenReturn(esperado);

        ResponseEntity<UsuarioResponseDTO> respuesta = usuarioController.porDocumento("CC", "123");

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).isSameAs(esperado);
        verify(usuarioService).buscarPorDocumento("CC", "123");
    }

    @Test
    void porDocumento_tienePreAuthorizeSoloAdmin() throws NoSuchMethodException {
        Method method = UsuarioController.class.getMethod(
                "porDocumento", String.class, String.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('ADMIN')");
    }
}
