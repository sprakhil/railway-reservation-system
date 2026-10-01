package com.railway.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
public class CorsConfig {

    @Value("${app.frontend.origins:http://localhost:4200}")
    private String frontendOrigins;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        var origins = Arrays.stream(frontendOrigins.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toList();
        if (origins.isEmpty() || origins.contains("*")) {
            throw new IllegalStateException("Configure explicit frontend origins; credentialed CORS cannot use '*'.");
        }
        corsConfig.setAllowedOrigins(origins);
        corsConfig.setMaxAge(3600L); // Cache preflight response for 1 hour
        corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        corsConfig.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Idempotency-Key", "X-Request-ID"));
        corsConfig.setExposedHeaders(Arrays.asList("X-Request-ID", "Content-Disposition"));
        corsConfig.setAllowCredentials(true);

        // Reactive CORS source (Because Gateway uses WebFlux)
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}
