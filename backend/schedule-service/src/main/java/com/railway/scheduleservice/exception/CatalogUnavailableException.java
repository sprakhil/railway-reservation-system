package com.railway.scheduleservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(String message) {
        super(message);
    }
}