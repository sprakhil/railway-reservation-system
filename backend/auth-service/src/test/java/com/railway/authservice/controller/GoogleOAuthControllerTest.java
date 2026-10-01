package com.railway.authservice.controller;

import com.railway.authservice.dto.AuthResponse;
import com.railway.authservice.dto.GoogleAuthCodeReq;
import com.railway.authservice.exception.CustomAuthException;
import com.railway.authservice.service.AuthService;
import com.railway.authservice.service.GoogleOAuthClient;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Clock;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GoogleOAuthControllerTest {
    private final GoogleOAuthClient google = mock(GoogleOAuthClient.class);
    private final AuthService auth = mock(AuthService.class);
    private final GoogleOAuthController controller = new GoogleOAuthController(google, auth);

    @Test
    void refusesMissingOrMismatchedStateBeforeCallingGoogle() {
        when(google.authorizationUrl(anyString(), anyString())).thenReturn("https://accounts.google.com/login");
        var request = new MockHttpServletRequest();
        controller.start(request);
        assertThrows(CustomAuthException.class, () -> controller.callback("code", "wrong-state", null, request));
        verify(google, never()).exchange(any(), any());
        verifyNoInteractions(auth);
    }

    @Test
    void returnsOnlyBrowserBoundOneTimeCodeAndNeverTokensInRedirect() {
        String[] state = new String[1];
        when(google.authorizationUrl(anyString(), anyString())).thenAnswer(call -> {
            state[0] = call.getArgument(0); return "https://accounts.google.com/login";
        });
        var identity = new GoogleOAuthClient.Identity("subject", "person@gmail.com", true, null);
        when(google.exchange(eq("raw+code"), anyString())).thenReturn(identity);
        when(auth.loginWithGoogle(identity)).thenReturn(new AuthResponse("private-access", "private-refresh", "person", "PASSENGER"));
        var request = new MockHttpServletRequest();
        controller.start(request);
        var callback = controller.callback("raw+code", state[0], null, request);
        String redirect = callback.getHeaders().getLocation().toString();
        assertTrue(redirect.startsWith("http://localhost:4200/auth/callback?code="));
        assertFalse(redirect.contains("private-"));
        String code = UriComponentsBuilder.fromUriString(redirect).build().getQueryParams().getFirst("code");
        var body = new GoogleAuthCodeReq(); body.setCode(code);
        assertThrows(CustomAuthException.class, () -> controller.exchange(body, new MockHttpServletRequest()));
        assertEquals("private-access", controller.exchange(body, request).getBody().getAccessToken());
        assertThrows(CustomAuthException.class, () -> controller.exchange(body, request));
        verify(google).exchange(eq("raw+code"), anyString());
    }

    @Test
    void deniedConsentConsumesStateAndRedirectsWithFixedError() {
        String[] state = new String[1];
        when(google.authorizationUrl(anyString(), anyString())).thenAnswer(call -> {
            state[0] = call.getArgument(0); return "https://accounts.google.com/login";
        });
        var request = new MockHttpServletRequest(); controller.start(request);
        var result = controller.callback(null, state[0], "access_denied", request);
        assertTrue(result.getHeaders().getLocation().toString().contains("google_sign_in_cancelled"));
        assertThrows(CustomAuthException.class, () -> controller.callback("code", state[0], null, request));
        verify(google, never()).exchange(any(), any());
    }

    @Test
    void expiredStateNeverExchangesAuthorizationCode() {
        var clock = mock(Clock.class);
        var now = Instant.parse("2026-09-16T12:00:00Z");
        when(clock.instant()).thenReturn(now, now.plusSeconds(300));
        ReflectionTestUtils.setField(controller, "clock", clock);
        String[] state = new String[1];
        when(google.authorizationUrl(anyString(), anyString())).thenAnswer(call -> {
            state[0] = call.getArgument(0); return "https://accounts.google.com/login";
        });
        var request = new MockHttpServletRequest(); controller.start(request);
        assertThrows(CustomAuthException.class, () -> controller.callback("code", state[0], null, request));
        verify(google, never()).exchange(any(), any());
    }

    @Test
    void exchangeExpiresAtSixtySecondsAndUsesTheOriginalNonce() {
        var clock = mock(Clock.class);
        var now = Instant.parse("2026-09-16T12:00:00Z");
        when(clock.instant()).thenReturn(now);
        ReflectionTestUtils.setField(controller, "clock", clock);
        String[] stateAndNonce = new String[2];
        when(google.authorizationUrl(anyString(), anyString())).thenAnswer(call -> {
            stateAndNonce[0] = call.getArgument(0); stateAndNonce[1] = call.getArgument(1);
            return "https://accounts.google.com/login";
        });
        var identity = new GoogleOAuthClient.Identity("subject", "person@gmail.com", true, null);
        when(google.exchange(eq("code"), anyString())).thenReturn(identity);
        when(auth.loginWithGoogle(identity)).thenReturn(new AuthResponse("private-access", "private-refresh", "person", "PASSENGER"));
        var request = new MockHttpServletRequest(); controller.start(request);
        var location = controller.callback("code", stateAndNonce[0], null, request).getHeaders().getLocation();
        verify(google).exchange("code", stateAndNonce[1]);
        var body = new GoogleAuthCodeReq();
        body.setCode(UriComponentsBuilder.fromUri(location).build().getQueryParams().getFirst("code"));
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        assertThrows(CustomAuthException.class, () -> controller.exchange(body, request));
    }
}
