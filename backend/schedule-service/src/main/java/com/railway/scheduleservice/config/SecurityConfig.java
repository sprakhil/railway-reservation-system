package com.railway.scheduleservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public route for searching train schedules
                        .requestMatchers(HttpMethod.GET,
                                "/api/schedules/**",
                                "/api/schedules/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        // Only Admins can upload new timetables
                        .requestMatchers(HttpMethod.POST, "/api/schedules/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        http.exceptionHandling(errors->errors.authenticationEntryPoint((req,res,ex)->com.railway.scheduleservice.exception.ApiErrors.write(req,res,401,"Authentication required"))
                .accessDeniedHandler((req,res,ex)->com.railway.scheduleservice.exception.ApiErrors.write(req,res,403,"You do not have permission for this operation")));
        return http.build();
    }
}