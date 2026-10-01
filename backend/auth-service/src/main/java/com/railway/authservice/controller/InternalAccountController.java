package com.railway.authservice.controller;

import com.railway.authservice.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** Minimal account lookup for the profile service; never exposes credentials or personal details. */
@RestController
@RequiredArgsConstructor
public class InternalAccountController {
    private final AuthUserRepository users;
    @Value("${railway.internal.token:}") private String internalToken;

    @GetMapping("/internal/auth/accounts/{username}/exists")
    public Map<String, Object> exists(@PathVariable String username,
            @RequestHeader(value = "X-Service-Token", required = false) String supplied) {
        if (internalToken == null || internalToken.length() < 32 || supplied == null
                || !MessageDigest.isEqual(internalToken.getBytes(StandardCharsets.UTF_8),
                    supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid service credentials");
        }
        if (users.existsByUsername(username)) return Map.of("exists", true, "accountUsername", username);
        return users.findByPublicUsernameIgnoreCase(username)
            .<Map<String,Object>>map(u -> Map.of("exists",true,"accountUsername",u.getUsername()))
            .orElseGet(() -> Map.of("exists",false));
    }
}
