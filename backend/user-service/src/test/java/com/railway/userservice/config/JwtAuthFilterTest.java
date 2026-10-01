package com.railway.userservice.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthFilterTest {
    private static final String SECRET = "test-only-jwt-secret-with-at-least-32-bytes";
    private final JwtUtils jwt = new JwtUtils();
    private final SessionVerifier sessions = org.mockito.Mockito.mock(SessionVerifier.class);
    private final JwtAuthFilter filter = new JwtAuthFilter(jwt,sessions);

    JwtAuthFilterTest() { org.mockito.Mockito.when(sessions.active(org.mockito.ArgumentMatchers.any())).thenReturn(true); ReflectionTestUtils.setField(jwt, "secret", SECRET); }
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    private String token(String subject, Object role, Instant expiry, String secret) {
        return Jwts.builder().subject(subject).claim("role", role).expiration(Date.from(expiry))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private void assertRejected(String token) throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer " + token);
        var response = new MockHttpServletResponse();
        var called = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> called.set(true));
        assertEquals(401, response.getStatus());
        assertFalse(called.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test void expiredOrTamperedTokenNeverReachesController() throws Exception {
        assertRejected(token("alice", "PASSENGER", Instant.now().minusSeconds(5), SECRET));
        assertRejected(token("alice", "ADMIN", Instant.now().plusSeconds(60), SECRET + "other-key"));
        assertRejected("not-a-jwt");
    }

    @Test void missingSubjectAndInvalidRoleNeverAuthenticate() throws Exception {
        assertRejected(token(null, "PASSENGER", Instant.now().plusSeconds(60), SECRET));
        assertRejected(token("alice", "UNKNOWN", Instant.now().plusSeconds(60), SECRET));
        assertRejected(token("alice", 1, Instant.now().plusSeconds(60), SECRET));
    }

    @Test void validTokenSetsIdentityAndRoleOnce() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer " + token("alice", "PASSENGER", Instant.now().plusSeconds(60), SECRET));
        var called = new AtomicBoolean();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> called.set(true));
        assertTrue(called.get());
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("alice", authentication.getName());
        assertTrue(authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_PASSENGER")));
    }
}
