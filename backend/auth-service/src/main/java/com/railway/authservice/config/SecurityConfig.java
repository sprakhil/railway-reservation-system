package com.railway.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@lombok.RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // Encrypts user passwords
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api/auth/v3/api-docs/**",
                                "/api/auth/swagger-ui/**"
                        ).permitAll()
                        .requestMatchers("/api/auth/logout","/api/auth/logout-all","/api/auth/admin-invitations/accept","/api/auth/username").authenticated()
                        .requestMatchers("/api/auth/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/auth/**","/internal/auth/session-check", "/internal/auth/accounts/*/exists").permitAll()
                        .anyRequest().authenticated()
                );
        http.addFilterBefore(jwtAuthFilter,org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        http.exceptionHandling(errors->errors.authenticationEntryPoint((req,res,ex)->com.railway.authservice.exception.ApiErrors.write(req,res,401,"Authentication required"))
                .accessDeniedHandler((req,res,ex)->com.railway.authservice.exception.ApiErrors.write(req,res,403,"You do not have permission for this operation")));
        return http.build();
    }
}
