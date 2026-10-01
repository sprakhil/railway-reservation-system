package com.railway.userservice.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.Map;

@Component
public class AuthAccountClient {
    private final RestClient client;
    private final String token;

    @Autowired
    public AuthAccountClient(@Value("${railway.auth.url:http://localhost:8081}") String url,
            @Value("${railway.internal.token:}") String token) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.token = token;
    }

    AuthAccountClient(RestClient client, String token) {
        this.client = client;
        this.token = token;
    }

    public boolean exists(String username) {
        return canonicalUsername(username).isPresent();
    }

    public java.util.Optional<String> canonicalUsername(String username) {
        if (token == null || token.length() < 32) {
            throw new RestClientException("Account service credentials are not configured");
        }
        Map<?, ?> result = client.get().uri("/internal/auth/accounts/{username}/exists", username)
                .header("X-Service-Token", token).retrieve().body(Map.class);
        if (result == null || !(result.get("exists") instanceof Boolean)) {
            throw new RestClientException("Invalid account service response");
        }
        if (!Boolean.TRUE.equals(result.get("exists"))) return java.util.Optional.empty();
        Object canonical=result.get("accountUsername");
        return java.util.Optional.of(canonical instanceof String value && !value.isBlank()?value:username);
    }
}
