package com.railway.chatbotservice.config;
import org.springframework.context.annotation.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
@Configuration
public class OpenApiConfig {
    @Bean OpenAPI api(){return new OpenAPI().info(new Info().title("Railwize — AI assistant").version("1.0").description("Gemini Developer API only. Read-only train search and private booking lookup. No Redis, Docker or database. Conversation history resets on service restart."))
        .servers(List.of(new Server().url("/").description("Use the gateway on port 8080")))
        .components(new Components().addSecuritySchemes("bearerAuth",new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));}
}
