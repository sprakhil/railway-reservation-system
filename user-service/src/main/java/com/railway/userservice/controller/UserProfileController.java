package com.railway.userservice.controller;

import com.railway.userservice.dto.UserProfileDto;
import com.railway.userservice.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService profileService;

    // Get the currently logged-in user's profile
    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> getMyProfile(Authentication authentication) {
        // authentication.getName() securely extracts the username from the verified JWT
        String username = authentication.getName();
        return ResponseEntity.ok(profileService.getProfileByUsername(username));
    }

    // Update or create the currently logged-in user's profile
    @PutMapping("/me")
    public ResponseEntity<UserProfileDto> updateMyProfile(
            @Valid @RequestBody UserProfileDto profileDto,
            Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(profileService.createOrUpdateProfile(username, profileDto));
    }

    // Admin endpoint: Get any user's profile
    @GetMapping("/{username}")
    public ResponseEntity<UserProfileDto> getUserProfileByAdmin(@PathVariable String username) {
        return ResponseEntity.ok(profileService.getProfileByUsername(username));
    }
}