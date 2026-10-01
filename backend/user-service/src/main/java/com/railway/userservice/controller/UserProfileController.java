package com.railway.userservice.controller;

import com.railway.userservice.dto.UserProfileDto;
import com.railway.userservice.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService profileService;
    private final com.railway.userservice.service.AuthAccountClient accounts;

    // Get the currently logged-in user's profile
    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> getMyProfile(Authentication authentication, jakarta.servlet.http.HttpServletRequest request) {
        // authentication.getName() securely extracts the username from the verified JWT
        String username = authentication.getName();
        var profile=profileService.getProfileByUsername(username);
        profile.setEmail((String)request.getAttribute("loggedInUserEmail"));
        return ResponseEntity.ok(profile);
    }

    // Update or create the currently logged-in user's profile
    @PutMapping("/me")
    public ResponseEntity<UserProfileDto> updateMyProfile(
            @Valid @RequestBody UserProfileDto profileDto,
            Authentication authentication, jakarta.servlet.http.HttpServletRequest request) {
        String username = authentication.getName();
        var profile=profileService.createOrUpdateProfile(username, profileDto);
        profile.setEmail((String)request.getAttribute("loggedInUserEmail"));
        return ResponseEntity.ok(profile);
    }

    // Admin endpoint: Get any user's profile
    @GetMapping("/{username}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserProfileDto> getUserProfileByAdmin(@PathVariable String username) {
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (username.equals(auth.getName())) return ResponseEntity.ok(profileService.getProfileByUsername(username));
        boolean admin=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN"));
        var canonical=accounts.canonicalUsername(username);
        if (!admin && (canonical.isEmpty() || !canonical.get().equals(auth.getName())))
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission for this operation");
        String owner=canonical.orElseThrow(()->new com.railway.userservice.exception.ResourceNotFoundException("Account not found"));
        return ResponseEntity.ok(profileService.getProfileByUsername(owner));
    }
}
