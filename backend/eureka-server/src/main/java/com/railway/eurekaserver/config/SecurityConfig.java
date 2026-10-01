package com.railway.eurekaserver.config;

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
            res.setHeader("WWW-Authenticate", "Basic realm=\"Railway eureka-server\"");
            com.railway.eurekaserver.exception.ApiErrors.write(req, res, 401, "Authentication required");
        };
        http
                // Disable CSRF for Eureka client registration endpoints
                .csrf(csrf -> csrf.ignoringRequestMatchers("/eureka/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> basic.authenticationEntryPoint(authenticationRequired));

        http.exceptionHandling(errors->errors.authenticationEntryPoint(authenticationRequired)
                .accessDeniedHandler((req,res,ex)->com.railway.eurekaserver.exception.ApiErrors.write(req,res,403,"You do not have permission for this operation")));
        return http.build();
    }
}