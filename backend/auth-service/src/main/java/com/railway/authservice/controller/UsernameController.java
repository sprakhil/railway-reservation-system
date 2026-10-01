package com.railway.authservice.controller;

import com.railway.authservice.service.UsernameService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth/username") @RequiredArgsConstructor
public class UsernameController {
    private final UsernameService usernames;
    public record Change(@NotBlank @Size(max=100) String username) {}
    @GetMapping public UsernameService.UsernameInfo current(Authentication auth) { return usernames.current(auth.getName()); }
    @PutMapping public UsernameService.UsernameInfo change(Authentication auth, @Valid @RequestBody Change request) {
        return usernames.change(auth.getName(),request.username());
    }
}
