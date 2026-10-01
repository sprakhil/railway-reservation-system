package com.railway.authservice.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.railway.authservice.exception.CustomAuthException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoogleOAuthClientTest {
    private final GoogleOAuthClient client = new GoogleOAuthClient();

    private GoogleIdToken verifiedToken(String nonce, boolean emailVerified) {
        var payload = new GoogleIdToken.Payload();
        payload.setSubject("google-subject");
        payload.setEmail("person@gmail.com");
        payload.setEmailVerified(emailVerified);
        payload.set("nonce", nonce);
        return new GoogleIdToken(new JsonWebSignature.Header(), payload, new byte[0], new byte[0]);
    }

    @Test void rejectsDifferentOrMissingNonceAfterProviderVerification() {
        assertThrows(CustomAuthException.class, () -> client.identityFromVerifiedToken(verifiedToken("wrong", true), "expected"));
        assertThrows(CustomAuthException.class, () -> client.identityFromVerifiedToken(verifiedToken(null, true), "expected"));
        assertThrows(CustomAuthException.class, () -> client.identityFromVerifiedToken(verifiedToken("nonce", true), null));
        assertThrows(CustomAuthException.class, () -> client.identityFromVerifiedToken(null, "nonce"));
    }

    @Test void rejectsUnverifiedEmailAndAcceptsVerifiedIdentityWithMatchingNonce() {
        assertThrows(CustomAuthException.class, () -> client.identityFromVerifiedToken(verifiedToken("nonce", false), "nonce"));
        var identity = client.identityFromVerifiedToken(verifiedToken("nonce", true), "nonce");
        assertEquals("google-subject", identity.subject());
        assertTrue(identity.emailVerified());
    }
}
