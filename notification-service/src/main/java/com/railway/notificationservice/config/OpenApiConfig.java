package com.railway.notificationservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        // This forces Swagger to use the Gateway's URL instead of the internal microservice port
        return new OpenAPI().addServersItem(new Server().url("/"));
    }
}