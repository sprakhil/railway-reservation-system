package com.railway.authservice.controller;

import com.railway.authservice.dto.AuthResponse;
import com.railway.authservice.dto.GoogleAuthCodeReq;
import com.railway.authservice.exception.CustomAuthException;
import com.railway.authservice.service.AuthService;
import com.railway.authservice.service.GoogleOAuthClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.Clock;
import java.util.Base64;

@RestController
@RequestMapping("/api/auth/google")
@RequiredArgsConstructor
public class GoogleOAuthController {
    private final GoogleOAuthClient google;
    private final AuthService authService;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock = Clock.systemUTC();
    @Value("${app.frontend.callback-uri:http://localhost:4200/auth/callback}")
    private String frontendCallback = "http://localhost:4200/auth/callback";
    private record Attempt(String state, String nonce, Instant expiresAt) {}
    private record LoginExchange(String code, AuthResponse response, Instant expiresAt) {}

    @GetMapping("/start")
    public ResponseEntity<Void> start(HttpServletRequest request) {
        String state = randomCode();
        String nonce = randomCode();
        String url = google.authorizationUrl(state, nonce);
        HttpSession session = request.getSession(true);
        synchronized (session) {
            session.removeAttribute("google.exchange");
            session.setAttribute("google.attempt", new Attempt(state, nonce, clock.instant().plusSeconds(300)));
            session.setMaxInactiveInterval(300);
        }
        return redirect(url);
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
            @RequestParam(required = false) String state, @RequestParam(required = false) String error,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) throw new CustomAuthException("Google sign-in session expired. Please start again.");
        Attempt attempt;
        synchronized (session) {
            attempt = (Attempt) session.getAttribute("google.attempt");
            session.removeAttribute("google.attempt");
        }
        if (attempt == null || !attempt.expiresAt().isAfter(clock.instant()) || !same(attempt.state(), state)) {
            throw new CustomAuthException("Invalid Google sign-in state. Please start again.");
        }
        if (error != null || code == null || code.isBlank()) {
            return redirect(UriComponentsBuilder.fromUriString(frontendCallback).queryParam("error", "google_sign_in_cancelled").build().encode().toUriString());
        }
        AuthResponse response = authService.loginWithGoogle(google.exchange(code, attempt.nonce()));
        request.changeSessionId();
        String exchangeCode = randomCode();
        synchronized (session) {
            session.setAttribute("google.exchange", new LoginExchange(exchangeCode, response, clock.instant().plusSeconds(60)));
            session.setMaxInactiveInterval(60);
        }
        return redirect(UriComponentsBuilder.fromUriString(frontendCallback).queryParam("code", exchangeCode).build().encode().toUriString());
    }

    // The code is valid for 60 seconds, once, and only in the browser that began sign-in.
    @PostMapping("/exchange")
    public ResponseEntity<AuthResponse> exchange(@Valid @RequestBody GoogleAuthCodeReq body, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) throw new CustomAuthException("Google exchange session expired.");
        LoginExchange exchange;
        synchronized (session) {
            exchange = (LoginExchange) session.getAttribute("google.exchange");
            if (exchange == null || !exchange.expiresAt().isAfter(clock.instant()) || !same(exchange.code(), body.getCode())) {
                throw new CustomAuthException("Invalid or expired Google exchange code.");
            }
            session.removeAttribute("google.exchange");
        }
        session.invalidate();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(exchange.response());
    }

    private ResponseEntity<Void> redirect(String uri) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(uri))
                .cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer").build();
    }

    private String randomCode() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean same(String expected, String supplied) {
        return supplied != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }
}
