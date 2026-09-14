package com.railway.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleAuthCodeReq {
    @NotBlank(message = "Authorization code is required")
    private String code;
}