package com.railway.authservice.config;

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
                .info(new Info().title("Railway Reservation - Authentication and administration").version("1.0")
                        .description("Register and verify email, log in, refresh or revoke sessions, use Google sign-in, and issue controlled admin invitations. Registration never accepts an admin role.\n\nUse the gateway Swagger UI at http://localhost:8080/swagger-ui.html. Select a service in the dropdown. Authorize once with the raw accessToken (no Bearer prefix); the same bearerAuth scheme is shared across service definitions in that page. Login/refresh does not fill it automatically. Replace all example IDs, emails and tokens with your own returned values."))
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
            document(api, "/api/auth/register", PathItem.HttpMethod.POST, "Account access", "Register a passenger",
                    "Use an email inbox you control. Read the emailed OTP and verify before login. No role field is accepted.", true, "{\"username\":\"demo_user\",\"email\":\"your-email@example.com\",\"password\":\"DemoPass@123\"}");
            document(api, "/api/auth/verify-otp", PathItem.HttpMethod.POST, "Account access", "Verify registration OTP",
                    "Replace the sample OTP with the six-digit code sent to your inbox.", true, "{\"email\":\"your-email@example.com\",\"otp\":\"123456\"}");
            document(api, "/api/auth/resend-otp", PathItem.HttpMethod.POST, "Account access", "Resend registration OTP",
                    "Supply email as a query parameter; no JSON body.", true, null);
            document(api, "/api/auth/login", PathItem.HttpMethod.POST, "Account access", "Log in with username or email",
                    "Copy accessToken from the response into Authorize. Login does not automatically authorize Swagger.", true, "{\"identifier\":\"demo_user\",\"password\":\"DemoPass@123\"}");
            document(api, "/api/auth/refresh", PathItem.HttpMethod.POST, "Account access", "Refresh access token",
                    "Use your actual refreshToken and update Swagger Authorize with the returned accessToken.", true, "{\"refreshToken\":\"REPLACE_WITH_YOUR_REFRESH_TOKEN\"}");
            document(api, "/api/auth/forgot-password", PathItem.HttpMethod.POST, "Password recovery", "Request a password-reset OTP",
                    "Supply email as a query parameter; no JSON body.", true, null);
            document(api, "/api/auth/reset-password", PathItem.HttpMethod.POST, "Password recovery", "Reset password with emailed OTP",
                    "Use the password-reset OTP, not a registration OTP. Existing sessions are revoked; sign in again.", true, "{\"email\":\"your-email@example.com\",\"otp\":\"123456\",\"newPassword\":\"NewDemoPass@456\"}");
            document(api, "/api/auth/logout", PathItem.HttpMethod.POST, "Sessions", "Log out this session",
                    "No JSON body. Returns 204. Also click Logout in Swagger Authorize to remove its saved token.", false, null);
            document(api, "/api/auth/username", PathItem.HttpMethod.GET, "Account", "Read my username and change allowance",
                    "The display/login username may be changed once. The session username remains the immutable internal ownership key.", false, null);
            document(api, "/api/auth/username", PathItem.HttpMethod.PUT, "Account", "Choose my username once",
                    "4-20 letters, digits or underscores; stored in lowercase. One successful change per account; identical retries do not consume another change. Existing bookings remain attached.", false, "{\"username\":\"your_new_name\"}");
            document(api, "/api/auth/logout-all", PathItem.HttpMethod.POST, "Sessions", "Log out every session",
                    "No JSON body. Returns 204. Also click Logout in Swagger Authorize to remove its saved token.", false, null);
            document(api, "/api/auth/google/start", PathItem.HttpMethod.GET, "Google sign-in", "Start Google sign-in in a browser",
                    "Open http://localhost:8080/api/auth/google/start in a new browser tab. Swagger fetch cannot complete the Google redirect and consent screen.", true, null);
            document(api, "/api/auth/google/callback", PathItem.HttpMethod.GET, "Google sign-in", "Receive Google OAuth callback",
                    "Provider callback, not a manually invoked login endpoint. Requires the browser session and matching state created by /start.", true, null);
            document(api, "/api/auth/google/exchange", PathItem.HttpMethod.POST, "Google sign-in", "Exchange the one-time browser code",
                    "Use the code from the frontend callback URL within 60 seconds, in the same browser that started sign-in. It is not the Google authorization code.", true, "{\"code\":\"REPLACE_WITH_ONE_TIME_FRONTEND_CALLBACK_CODE\"}");
            document(api, "/api/auth/admin/invitations", PathItem.HttpMethod.POST, "Admin invitations", "Invite a verified account to become admin",
                    "ADMIN only. The invitation secret is sent by email; it is not returned here.", false, "{\"email\":\"invited-person@example.com\"}");
            document(api, "/api/auth/admin/invitations", PathItem.HttpMethod.GET, "Admin invitations", "List administrator invitations",
                    "ADMIN only. Page is zero-based; maximum size is 100.", false, null);
            document(api, "/api/auth/admin/invitations/{id}", PathItem.HttpMethod.DELETE, "Admin invitations", "Revoke an unused invitation",
                    "ADMIN only. Use the invitation ID returned when listing.", false, null);
            document(api, "/api/auth/admin-invitations/accept", PathItem.HttpMethod.POST, "Admin invitations", "Accept an emailed admin invitation",
                    "Sign in as the verified invited email address. Acceptance revokes existing sessions; log in again for an ADMIN token.", false, "{\"token\":\"REPLACE_WITH_THE_FULL_TOKEN_FROM_YOUR_INVITATION_EMAIL\"}");
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
