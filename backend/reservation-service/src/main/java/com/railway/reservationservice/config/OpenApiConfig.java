package com.railway.reservationservice.config;

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
                .info(new Info().title("Railway Reservation - Search, booking and tickets").version("1.0")
                        .description("One train/class and 1-6 passengers per booking. Two-minute payment holds. Whole-booking CNF or WL, no RAC. Paid WL promotes automatically when the entire booking fits; remaining WL closes one hour before boarding and is refunded in test mode.\n\nUse the gateway Swagger UI at http://localhost:8080/swagger-ui.html. Select a service in the dropdown. Authorize once with the raw accessToken (no Bearer prefix); the same bearerAuth scheme is shared across service definitions in that page. Login/refresh does not fill it automatically. Replace all example IDs, emails and tokens with your own returned values."))
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
            document(api, "/api/reservations/alternatives", PathItem.HttpMethod.GET, "Journey search", "Plan connecting trains and nearby stations",
                    "Public, read-only. seatClass defaults to ALL; a selected class must be offered on both connecting legs. Nearby stations are considered at BOTH endpoints within radiusKm (1-100). Nearby direct trains are evaluated before one-interchange journeys (default 60-360 minute transfer). stationNames maps codes to readable names. Each leg is booked and paid separately. Notices describe missing coordinates and bounded/partial results. Distances are straight-line estimates; fare excludes local transport.", true, null);
            document(api, "/api/reservations/search", PathItem.HttpMethod.GET, "Journey search", "Search matching journeys",
                    "Public. Use station codes and a boarding date from today through today plus two calendar months (Asia/Kolkata), inclusive. Availability and fare are rechecked during booking.", true, null);
            document(api, "/api/reservations/trains", PathItem.HttpMethod.GET, "Journey search", "Search trains across all classes",
                    "Public. Only fromStation, toStation and dateOfJourney are required. One result per train; classes contains per-passenger fares and current segment availability for every configured class. No booking or hold is created. Select a class, then use /quote with the actual passenger count. CNF, WL and UNAVAILABLE are distinct outcomes.", true, null);
            document(api, "/api/reservations/quote", PathItem.HttpMethod.GET, "Journey search", "Quote one train and journey",
                    "Public. Use station codes and a boarding date from today through today plus two calendar months (Asia/Kolkata), inclusive. Availability and fare are rechecked during booking.", true, null);
            document(api, "/api/reservations/book", PathItem.HttpMethod.POST, "Bookings", "Create a two-minute checkout hold",
                    "Requires Idempotency-Key (8-80 letters, digits, dot, underscore or hyphen). Copy the quote total into expectedTotalFare. Replay the same key and body after a network interruption; use a new key for a new booking. All passengers share CNF/WL allocation.", false, "{\"trainNumber\":\"DM001\",\"fromStation\":\"DSTA\",\"toStation\":\"DSTC\",\"seatClass\":\"SL\",\"dateOfJourney\":\"NEXT_BOARDING_DATE\",\"expectedTotalFare\":240,\"passengers\":[{\"name\":\"Demo Traveller\",\"age\":25,\"gender\":\"OTHER\",\"birthPreference\":\"LOWER\"},{\"name\":\"Second Traveller\",\"age\":30,\"gender\":\"FEMALE\",\"birthPreference\":\"NONE\"}]}");
            document(api, "/api/reservations/my-bookings", PathItem.HttpMethod.GET, "Bookings", "List and filter my booking history",
                    "Zero-based pagination; size 1-100. Sort: bookingTime, dateOfJourney, trainNumber, status, pnr. Status accepts CNF/WL or PENDING_PAYMENT, CONFIRMED, WAITING, EXPIRED, CANCELLED. fromDate/toDate filter journey dates inclusively.", false, null);
            document(api, "/api/reservations/pnr/{pnr}", PathItem.HttpMethod.GET, "Bookings", "Read a booking by PNR",
                    "Owner or ADMIN. CONFIRMED means CNF; WAITING means paid WL. PENDING_PAYMENT is not a paid ticket.", false, null);
            document(api, "/api/reservations/{pnr}/cancellation-preview", PathItem.HttpMethod.GET, "Cancellation", "Preview cancellation and test refund",
                    "Owner or ADMIN. Default paid CNF cancellation fee is 20%; paid WL is fully refundable. Status can change before cancellation.", false, null);
            document(api, "/api/reservations/{pnr}", PathItem.HttpMethod.DELETE, "Cancellation", "Cancel the entire booking",
                    "Owner or ADMIN, before boarding. No individual-passenger cancellation. Refund completion is asynchronous; poll payment status.", false, null);
            document(api, "/api/reservations/{pnr}/ticket.pdf", PathItem.HttpMethod.GET, "Tickets", "Download a PDF ticket",
                    "Owner or ADMIN; verified captured test payment is required. Download response is application/pdf, not JSON.", false, null);
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
