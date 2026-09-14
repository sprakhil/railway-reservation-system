package com.railway.authservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        String aestheticDescription = "<div style='font-family: inherit; padding: 10px; border-radius: 8px; background-color: #f4f6f8; border-left: 4px solid #005A9C;'>" +
                "<h3 style='margin-top: 0; color: #005A9C;'>🚄 Next-Gen Railway Booking Platform</h3>" +
                "<p>Welcome to the central nervous system of the Railway network.</p>" +
                "<b>Core Capabilities:</b>" +
                "<ul style='margin-bottom: 0;'>" +
                "<li>🔒 <b>OAuth2 & JWT</b> secured microservices</li>" +
                "<li>⚡ <b>High-Concurrency</b> ticket reservation engine</li>" +
                "<li>🌐 <b>API Gateway</b> routed architecture</li>" +
                "</ul>" +
                "</div>";

        return new OpenAPI()
                // 1. Keep the relative URL fix for the API Gateway
                .addServersItem(new Server().url("/"))

                // 2. Tell Swagger to require this security scheme globally
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))

                // 3. Customization
                .info(new Info()
                        .title("Railway Reservation API - Auth Service")
                        .description(aestheticDescription)
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Railway Backend Team")
                                .email("support@railway.com")
                                .url("https://github.com/your-repo"))
                        .license(new License().name("Apache 2.0").url("http://springdoc.org")))

                // 4. Define what the security scheme actually is (a JWT Bearer token)
                .components(
                        new Components()
                                .addSecuritySchemes(securitySchemeName,
                                        new SecurityScheme()
                                                .name(securitySchemeName)
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                )
                );
    }
}