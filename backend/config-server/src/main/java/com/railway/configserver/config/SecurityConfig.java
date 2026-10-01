package com.railway.configserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint authenticationRequired = (req, res, ex) -> {
            res.setHeader("WWW-Authenticate", "Basic realm=\"Railway config-server\"");
            com.railway.configserver.exception.ApiErrors.write(req, res, 401, "Authentication required");
        };
        // CSRF disabled because this is a stateless REST API serving configs
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll() // Allow health checks
                        .anyRequest().authenticated() // Secure all config requests
                )
                .httpBasic(basic -> basic.authenticationEntryPoint(authenticationRequired)); // Use Basic Auth
        http.exceptionHandling(errors->errors.authenticationEntryPoint(authenticationRequired)
                .accessDeniedHandler((req,res,ex)->com.railway.configserver.exception.ApiErrors.write(req,res,403,"You do not have permission for this operation")));
        return http.build();
    }
}