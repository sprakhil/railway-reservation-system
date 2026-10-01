package com.railway.authservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// This binds the exception to a specific HTTP status code (e.g., 401 Unauthorized or 400 Bad Request)
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class CustomAuthException extends RuntimeException {
    public CustomAuthException(String message) {
        super(message);
    }
}