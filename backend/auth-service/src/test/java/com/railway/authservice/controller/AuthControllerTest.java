package com.railway.authservice.controller;

import com.railway.authservice.dto.AuthResponse;
import com.railway.authservice.dto.LoginRequest;
import com.railway.authservice.dto.RegisterRequest;
import com.railway.authservice.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    void register_ShouldReturn201() {
        // Arrange
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser");

        when(authService.registerUser(any(RegisterRequest.class))).thenReturn("User registered successfully");

        // Act
        ResponseEntity<?> response = authController.register(req);

        // Assert
        assertNotNull(response);
        // FIXED: Your code correctly returns 201 CREATED!
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("User registered successfully", response.getBody());
    }

    @Test
    void login_ShouldReturn200AndToken() {
        // Arrange
        LoginRequest req = new LoginRequest();
        req.setIdentifier("user");

        AuthResponse mockResponse = new AuthResponse();
        mockResponse.setAccessToken("mock.jwt.token");

        when(authService.login(any(LoginRequest.class))).thenReturn(mockResponse);

        // Act
        ResponseEntity<AuthResponse> response = authController.login(req);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("mock.jwt.token", response.getBody().getAccessToken());
    }
}