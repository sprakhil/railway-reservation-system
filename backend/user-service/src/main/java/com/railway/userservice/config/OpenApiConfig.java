package com.railway.userservice.config;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Documentation only; runtime access remains enforced by Spring Security and ownership checks. */
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Railway Reservation - Profiles and passenger Master List").version("1.0")
                        .description("Manage your own profile and reusable passenger details. Saved passengers must be copied into the booking request; they are not booked by ID.\n\nUse the gateway Swagger UI at http://localhost:8080/swagger-ui.html. Select a service in the dropdown. Authorize once with the raw accessToken (no Bearer prefix); the same bearerAuth scheme is shared across service definitions in that page. Login/refresh does not fill it automatically. Replace all example IDs, emails and tokens with your own returned values."))
                .addServersItem(new Server().url("/").description("Same origin; use port 8080 for all services"))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Paste accessToken only. Passenger and ADMIN permissions are checked by the backend.")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    public OpenApiCustomizer railwayApiDocumentation() {
        return api -> {
            if (api.getPaths() == null) return;
            // Service credentials must never be pasted into the passenger/admin API explorer.
            api.getPaths().keySet().removeIf(path -> !path.startsWith("/api/") || path.equals("/api/auth/google"));
            document(api, "/api/users/me", PathItem.HttpMethod.GET, "Profile", "Read my profile",
                    "Registered accounts receive 200 with their username and empty optional fields until PUT /api/users/me saves the details. Missing accounts return 404; an unavailable account lookup returns 503.", false, null);
            document(api, "/api/users/me", PathItem.HttpMethod.PUT, "Profile", "Create or update my profile",
                    "The signed-in username is selected by the server.", false, "{\"firstName\":\"Demo\",\"lastName\":\"Traveller\",\"phoneNumber\":\"9876543210\",\"gender\":\"OTHER\",\"dateOfBirth\":\"2000-01-01\",\"address\":\"Demo address, Mumbai\"}");
            document(api, "/api/users/{username}", PathItem.HttpMethod.GET, "Profile", "Read a profile by username",
                    "Only the same user or an ADMIN can read this profile.", false, null);
            document(api, "/api/users/me/passengers", PathItem.HttpMethod.GET, "Passenger Master List", "List my saved passengers",
                    "Page is zero-based; size is 1-100. Records are sorted by name.", false, null);
            document(api, "/api/users/me/passengers", PathItem.HttpMethod.POST, "Passenger Master List", "Save a passenger",
                    "Owner only. Age is 1-120. Preferences are recorded but not guaranteed.", false, "{\"name\":\"Demo Traveller\",\"age\":25,\"gender\":\"OTHER\",\"berthPreference\":\"LOWER\"}");
            document(api, "/api/users/me/passengers/{id}", PathItem.HttpMethod.PUT, "Passenger Master List", "Update a saved passenger",
                    "Owner only. Changes do not modify existing booking snapshots.", false, "{\"name\":\"Demo Traveller\",\"age\":25,\"gender\":\"OTHER\",\"berthPreference\":\"LOWER\"}");
            document(api, "/api/users/me/passengers/{id}", PathItem.HttpMethod.DELETE, "Passenger Master List", "Delete a saved passenger",
                    "Owner only. Existing bookings remain unchanged.", false, null);
        };
    }

    private static void document(OpenAPI api, String path, PathItem.HttpMethod method, String tag,
            String summary, String description, boolean publicAccess, String example) {
        PathItem item = api.getPaths().get(path);
        if (item == null) return;
        Operation operation = item.readOperationsMap().get(method);
        if (operation == null) return;
        operation.setTags(List.of(tag));
        operation.setSummary(summary);
        operation.setDescription(description);
        // An explicit empty list overrides the global JWT requirement for public operations.
        if (publicAccess) operation.setSecurity(List.of());
        if (example != null && operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
            var json = operation.getRequestBody().getContent().get("application/json");
            if (json != null) {
                try {
                    String dated = example.replace("NEXT_BOARDING_DATE", LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1).toString());
                    json.setExample(Json.mapper().readValue(dated, Object.class));
                } catch (java.io.IOException error) {
                    throw new IllegalStateException("Invalid OpenAPI example for " + path, error);
                }
            }
        }
        if (operation.getParameters() != null) for (var parameter : operation.getParameters()) {
            switch (parameter.getName()) {
                case "Idempotency-Key" -> { parameter.setDescription("New booking: unique 8-80 character key. Retry: reuse the exact key and request body."); parameter.setExample("swagger-demo-booking-001"); }
                case "X-Razorpay-Signature" -> { parameter.setRequired(true); parameter.setDescription("Provider HMAC signature of the exact raw request body, not a JWT."); }
                case "dateOfJourney" -> parameter.setExample(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1).toString());
                case "fromStation" -> parameter.setExample("DSTA");
                case "toStation" -> parameter.setExample("DSTC");
                case "trainNumber" -> parameter.setExample("DM001");
                case "seatClass" -> parameter.setExample("SL");
                case "page" -> parameter.setDescription("Zero-based page number, minimum 0.");
                case "size" -> parameter.setDescription("Number of items per page, from 1 through 100.");
                default -> { }
            }
        }
    }
}
