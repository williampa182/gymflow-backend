package com.gymflow.backend.security.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimingSafeAuthenticationProviderTest {

    private static final String DUMMY_HASH =
            "$2a$12$N9qo8uLOickgx2ZMRZoMy.Mrq8VcVjKZB4T9qLCcFQv8Uu2mBbWCi";

    @Mock
    private AuthenticationProvider delegate;

    @Mock
    private PasswordEncoder passwordEncoder;

    private TimingSafeAuthenticationProvider authenticationProvider;

    @BeforeEach
    void setUp() {
        authenticationProvider = new TimingSafeAuthenticationProvider(delegate, passwordEncoder);
    }

    @Test
    void authenticate_delegacionExitosa_retornaLaAuthenticationSinUsarPasswordEncoder() {
        Authentication request = mock(Authentication.class);
        Authentication expected = mock(Authentication.class);
        when(delegate.authenticate(request)).thenReturn(expected);

        Authentication result = authenticationProvider.authenticate(request);

        assertThat(result).isSameAs(expected);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void authenticate_usuarioNoEncontrado_haceDummyHashYLanzaBadCredentialsGenerica() {
        Authentication request = mock(Authentication.class);
        when(request.getCredentials()).thenReturn("password-secreta");
        when(delegate.authenticate(request)).thenThrow(new UsernameNotFoundException("email inexistente"));
        when(passwordEncoder.matches("password-secreta", DUMMY_HASH)).thenReturn(false);

        assertThatThrownBy(() -> authenticationProvider.authenticate(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Email o contrase\u00f1a incorrectos");

        verify(passwordEncoder).matches("password-secreta", DUMMY_HASH);
        verify(passwordEncoder, times(1)).matches(any(), any());
    }

    @Test
    void authenticate_credencialesNulas_haceDummyHashConPasswordVacio() {
        Authentication request = mock(Authentication.class);
        when(request.getCredentials()).thenReturn(null);
        when(delegate.authenticate(request)).thenThrow(new UsernameNotFoundException("email inexistente"));
        when(passwordEncoder.matches("", DUMMY_HASH)).thenReturn(false);

        assertThatThrownBy(() -> authenticationProvider.authenticate(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Email o contrase\u00f1a incorrectos");

        verify(passwordEncoder).matches("", DUMMY_HASH);
    }

    @Test
    void authenticate_badCredentialsDelDelegate_lasPropagaSinHacerDummyHash() {
        Authentication request = mock(Authentication.class);
        BadCredentialsException expected = new BadCredentialsException("password incorrecta");
        when(delegate.authenticate(request)).thenThrow(expected);

        assertThatThrownBy(() -> authenticationProvider.authenticate(request))
                .isSameAs(expected);

        verify(passwordEncoder, never()).matches(any(), any());
    }
}
