package com.railway.trainservice.config;

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
                .info(new Info().title("Railway Reservation - Station and train catalogue").version("1.0")
                        .description("Public catalogue reads and ADMIN writes. Create stations first, then trains with explicit class capacity, fares and operating days.\n\nUse the gateway Swagger UI at http://localhost:8080/swagger-ui.html. Select a service in the dropdown. Authorize once with the raw accessToken (no Bearer prefix); the same bearerAuth scheme is shared across service definitions in that page. Login/refresh does not fill it automatically. Replace all example IDs, emails and tokens with your own returned values."))
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
            document(api, "/api/stations", PathItem.HttpMethod.GET, "Stations", "List stations",
                    "Public paginated list. Page starts at 0; size is 1-100.", true, null);
            document(api, "/api/stations/{stationId}", PathItem.HttpMethod.GET, "Stations", "Read a station",
                    "Public lookup; missing records return 404.", true, null);
            document(api, "/api/stations", PathItem.HttpMethod.POST, "Stations", "Create a station",
                    "ADMIN only. Station codes are trimmed and uppercased.", false, "{\"stationCode\":\"DSTA\",\"stationName\":\"Demo Origin\",\"city\":\"Demo City\",\"state\":\"Demo State\",\"zone\":\"DEMO\"}");
            document(api, "/api/stations/{stationId}", PathItem.HttpMethod.PUT, "Stations", "Update a station",
                    "ADMIN only. The established station code is immutable; display details may change.", false, "{\"stationCode\":\"DSTA\",\"stationName\":\"Demo Origin\",\"city\":\"Demo City\",\"state\":\"Demo State\",\"zone\":\"DEMO\"}");
            document(api, "/api/trains", PathItem.HttpMethod.GET, "Trains", "List trains",
                    "Public paginated list. Page starts at 0; size is 1-100.", true, null);
            document(api, "/api/trains/{trainNumber}", PathItem.HttpMethod.GET, "Trains", "Read a train",
                    "Public lookup; missing records return 404.", true, null);
            document(api, "/api/trains", PathItem.HttpMethod.POST, "Trains", "Create a train",
                    "ADMIN only. Replace sourceStationId/destinationStationId with actual station IDs. Empty capacity/days cannot be booked.", false, "{\"trainNumber\":\"DM001\",\"trainName\":\"Demo Small Capacity Express\",\"category\":\"EXPRESS\",\"sourceStationId\":1,\"destinationStationId\":3,\"seatClasses\":[\"SL\"],\"operatingDays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\",\"SATURDAY\",\"SUNDAY\"],\"classConfigurations\":[{\"seatClass\":\"SL\",\"coachCount\":1,\"seatsPerCoach\":2,\"baseFare\":20,\"ratePerKm\":0.5,\"minimumFare\":40}]}");
            document(api, "/api/trains/{trainNumber}", PathItem.HttpMethod.PUT, "Trains", "Update a train",
                    "ADMIN only. Established identity, route endpoints, capacity, classes and operating days are immutable; fares and display metadata may change.", false, "{\"trainNumber\":\"DM001\",\"trainName\":\"Demo Small Capacity Express\",\"category\":\"EXPRESS\",\"sourceStationId\":1,\"destinationStationId\":3,\"seatClasses\":[\"SL\"],\"operatingDays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\",\"SATURDAY\",\"SUNDAY\"],\"classConfigurations\":[{\"seatClass\":\"SL\",\"coachCount\":1,\"seatsPerCoach\":2,\"baseFare\":20,\"ratePerKm\":0.5,\"minimumFare\":40}]}");
            document(api, "/api/stations/code/{stationCode}", PathItem.HttpMethod.GET, "Stations", "Find a station by code",
                    "Codes are trimmed and converted to uppercase.", true, null);
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
