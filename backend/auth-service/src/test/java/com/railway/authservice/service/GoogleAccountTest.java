package com.railway.authservice.service;

import com.railway.authservice.config.JwtUtils;
import com.railway.authservice.entity.AuthUser;
import com.railway.authservice.repository.*;
import com.railway.authservice.exception.CustomAuthException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class GoogleAccountTest {
    @Mock AuthUserRepository users;
    @Mock RefreshTokenRepository refresh;
    @Mock PasswordEncoder passwords;
    @Mock JwtUtils jwt;
    @Mock OtpVerificationRepository otps;
    @Mock OtpEmailService email;
    @Mock private org.springframework.context.ApplicationEventPublisher events;
    @InjectMocks AuthService auth;

    @Test void refusesUnverifiedGoogleEmail() {
        assertThrows(CustomAuthException.class, () -> auth.loginWithGoogle(new GoogleOAuthClient.Identity("subject", "x@gmail.com", false, null)));
        verifyNoInteractions(users, refresh);
    }

    @Test void refusesTakeoverOfUnverifiedLocalAccount() {
        var user = AuthUser.builder().email("x@gmail.com").provider(AuthUser.Provider.LOCAL).build();
        when(users.findByEmailForUpdate("x@gmail.com")).thenReturn(Optional.of(user));
        assertThrows(CustomAuthException.class, () -> auth.loginWithGoogle(new GoogleOAuthClient.Identity("subject", "x@gmail.com", true, null)));
        verify(users, never()).save(any());
    }

    @Test void refusesAutomaticLinkForThirdPartyEmailNotAuthoritativeForGoogle() {
        var user = AuthUser.builder().email("x@example.com").isVerified(true).provider(AuthUser.Provider.LOCAL).build();
        when(users.findByEmailForUpdate("x@example.com")).thenReturn(Optional.of(user));
        assertThrows(CustomAuthException.class, () -> auth.loginWithGoogle(new GoogleOAuthClient.Identity("subject", "x@example.com", true, null)));
        verify(users, never()).save(any());
    }

    @Test void createsVerifiedGoogleAccountAndTokens() {
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));
        when(refresh.save(any())).thenAnswer(call -> call.getArgument(0));
        when(jwt.generateAccessToken(any(),any())).thenReturn("test-jwt");
        var result = auth.loginWithGoogle(new GoogleOAuthClient.Identity("subject", "x@gmail.com", true, null));
        assertEquals("test-jwt", result.getAccessToken());
        var captor = ArgumentCaptor.forClass(AuthUser.class); verify(users).save(captor.capture());
        assertTrue(captor.getValue().isVerified()); assertTrue(captor.getValue().isEmailVerified());
        assertEquals(AuthUser.Provider.GOOGLE, captor.getValue().getProvider());
        assertNull(captor.getValue().getPassword());
    }
}
