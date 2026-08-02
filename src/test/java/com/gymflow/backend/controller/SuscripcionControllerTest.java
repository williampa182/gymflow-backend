package com.gymflow.backend.controller;

import com.gymflow.backend.dto.SuscripcionResponseDTO;
import com.gymflow.backend.service.SuscripcionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuscripcionControllerTest {

    @Mock
    private SuscripcionService suscripcionService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private SuscripcionController suscripcionController;

    @Test
    void listarPropias_usaElEmailDelPrincipalYNoUnIdDelCliente() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<SuscripcionResponseDTO> pagina = new PageImpl<>(List.of());
        when(authentication.getName()).thenReturn("cliente@gymflow.test");
        when(suscripcionService.listarPorUsuarioEmail("cliente@gymflow.test", pageable))
                .thenReturn(pagina);

        ResponseEntity<Page<SuscripcionResponseDTO>> respuesta = suscripcionController
                .listarPropias(authentication, pageable);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).isSameAs(pagina);
        verify(suscripcionService).listarPorUsuarioEmail("cliente@gymflow.test", pageable);
        verifyNoMoreInteractions(suscripcionService);
    }
}
