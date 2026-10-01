package com.railway.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String username;
    private String role;
    private String displayUsername;
    private boolean usernameChangeAllowed;
    private boolean usernameSetupSuggested;

    public AuthResponse(String accessToken, String refreshToken, String username, String role) {
        this.accessToken=accessToken; this.refreshToken=refreshToken; this.username=username; this.role=role;
    }
}
