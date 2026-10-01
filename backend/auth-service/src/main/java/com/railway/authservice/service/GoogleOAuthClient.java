package com.railway.authservice.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.railway.authservice.exception.CustomAuthException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.Collections;

@Component
public class GoogleOAuthClient {
    @Value("${google.client.id:}") private String clientId;
    @Value("${google.client.secret:}") private String clientSecret;
    @Value("${google.redirect.uri:http://localhost:8080/api/auth/google/callback}") private String redirectUri;

    public record Identity(String subject, String email, boolean emailVerified, String hostedDomain) {}

    public String authorizationUrl(String state, String nonce) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new CustomAuthException("Google sign-in is not configured. Set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET.");
        }
        return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", clientId).queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code").queryParam("scope", "openid email profile")
                .queryParam("state", state).queryParam("nonce", nonce).queryParam("prompt", "select_account")
                .build().encode().toUriString();
    }

    public Identity exchange(String authorizationCode, String expectedNonce) {
        try {
            // Spring already decoded the query parameter; decoding again corrupts '+' in codes.
            var response = new GoogleAuthorizationCodeTokenRequest(new NetHttpTransport(), GsonFactory.getDefaultInstance(),
                    clientId, clientSecret, authorizationCode, redirectUri).execute();
            GoogleIdToken token = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(clientId)).build().verify(response.getIdToken());
            return identityFromVerifiedToken(token, expectedNonce);
        } catch (CustomAuthException e) {
            throw e;
        } catch (Exception e) {
            // Provider exceptions may contain authorization codes or credentials; never echo/log them.
            throw new CustomAuthException("Google sign-in failed or the code expired. Please try again.");
        }
    }

    // Called only after signature, issuer, audience and expiry verification above.
    Identity identityFromVerifiedToken(GoogleIdToken token, String expectedNonce) {
        if (token == null || expectedNonce == null || expectedNonce.isBlank()
                || !expectedNonce.equals(token.getPayload().get("nonce"))) {
            throw new CustomAuthException("Invalid Google identity or nonce. Restart Google sign-in.");
        }
        var payload = token.getPayload();
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) throw new CustomAuthException("Google email is not verified.");
        if (payload.getSubject() == null || payload.getSubject().isBlank()
                || payload.getEmail() == null || payload.getEmail().isBlank()) {
            throw new CustomAuthException("Google identity is missing required account details.");
        }
        return new Identity(payload.getSubject(), payload.getEmail(), true, payload.getHostedDomain());
    }
}
