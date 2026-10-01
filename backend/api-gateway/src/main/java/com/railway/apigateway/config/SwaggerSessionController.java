package com.railway.apigateway.config;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, non-secret identifier for this gateway run; never contains credentials. */
@RestController
public class SwaggerSessionController {
    private final String instanceId = UUID.randomUUID().toString();

    @GetMapping("/swagger-session")
    public ResponseEntity<Map<String, String>> session() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("instanceId", instanceId));
    }
}
