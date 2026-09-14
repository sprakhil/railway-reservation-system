package com.railway.authservice.service;

import com.railway.authservice.dto.AuthResponse;
import com.railway.authservice.dto.LoginRequest;
import com.railway.authservice.dto.RegisterRequest;
import com.railway.authservice.entity.AuthUser;
import com.railway.authservice.repository.AuthUserRepository;
import com.railway.authservice.repository.OtpVerificationRepository;
import com.railway.authservice.repository.RefreshTokenRepository; // ADDED
import com.railway.authservice.config.JwtUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthUserRepository userRepository;
    @Mock private OtpVerificationRepository otpRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository; // FIXED: Prevent NPE on login
    @Mock private OtpEmailService otpEmailService; // FIXED: Prevent NPE on registration
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtils jwtUtils;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_Success() {
        // Arrange
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser");
        req.setEmail("test@railway.com");
        req.setPassword("Password123!");

        // Use lenient() so Mockito doesn't complain if your code only uses existsBy OR findBy
        lenient().when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        lenient().when(userRepository.existsByEmail(anyString())).thenReturn(false);
        lenient().when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        lenient().when(userRepository.existsByUsername(anyString())).thenReturn(false);

        when(passwordEncoder.encode(anyString())).thenReturn("encoded_password");
        when(userRepository.save(any(AuthUser.class))).thenReturn(new AuthUser());

        // Mock the email sending process so it does nothing instead of crashing
        doNothing().when(otpEmailService).sendOtpEmail(anyString(), anyString());

        // Act
        String response = authService.registerUser(req);

        // Assert
        assertNotNull(response);
        verify(userRepository, times(1)).save(any(AuthUser.class));
        verify(otpRepository, times(1)).save(any());
        verify(otpEmailService, times(1)).sendOtpEmail(anyString(), anyString());
    }

    @Test
    void register_EmailAlreadyExists_ThrowsException() {
        // Arrange
        RegisterRequest req = new RegisterRequest();
        req.setEmail("duplicate@railway.com");
        req.setUsername("dupuser");

        // Mock BOTH existsBy and findBy to guarantee your security check trips!
        lenient().when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(new AuthUser()));
        lenient().when(userRepository.existsByEmail(anyString())).thenReturn(true);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> authService.registerUser(req));

        // Verify it was correctly blocked before saving
        verify(userRepository, never()).save(any(AuthUser.class));
    }

    @Test
    void login_Success_ReturnsJwt() {
        // Arrange
        LoginRequest req = new LoginRequest();
        req.setIdentifier("test@railway.com");
        req.setPassword("Password123!");

        AuthUser mockUser = new AuthUser();
        mockUser.setEmail("test@railway.com");
        mockUser.setPassword("encoded_password");
        mockUser.setVerified(true);

        // FIXED: Give the fake user a role so getRole().name() works!
        // Note: If your enum is named differently (e.g., CUSTOMER), change 'USER' to match it.
        mockUser.setRole(AuthUser.Role.PASSENGER);

        ReflectionTestUtils.setField(authService, "refreshExpirationMs", 86400000L);

        when(userRepository.findByUsernameOrEmail(anyString(), anyString())).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("Password123!", "encoded_password")).thenReturn(true);
        when(jwtUtils.generateAccessToken(mockUser)).thenReturn("mock.jwt.token");

        doNothing().when(refreshTokenRepository).deleteByUser(any(AuthUser.class));
        lenient().when(refreshTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AuthResponse response = authService.login(req);

        // Assert
        assertNotNull(response);
        assertEquals("mock.jwt.token", response.getAccessToken());
        verify(refreshTokenRepository, times(1)).deleteByUser(any(AuthUser.class));
        verify(refreshTokenRepository, times(1)).save(any());
    }

    @Test
    void login_InvalidPassword_ThrowsException() {
        // Arrange
        LoginRequest req = new LoginRequest();
        req.setIdentifier("test@railway.com");
        req.setPassword("WrongPassword!");

        AuthUser mockUser = new AuthUser();
        mockUser.setPassword("encoded_password");
        mockUser.setVerified(true);

        when(userRepository.findByUsernameOrEmail(anyString(), anyString())).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("WrongPassword!", "encoded_password")).thenReturn(false);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> authService.login(req));
        verify(jwtUtils, never()).generateAccessToken(any());
    }
}