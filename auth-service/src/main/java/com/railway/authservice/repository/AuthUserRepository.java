package com.railway.authservice.repository;

import com.railway.authservice.entity.AuthUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AuthUserRepository extends JpaRepository<AuthUser, Long> {
    boolean existsByUsername(String username);
    boolean existsByEmail(@Email(message = "Invalid email format") @NotBlank(message = "Email is required") String email);

    Optional<AuthUser> findByUsername(String username);
    Optional<AuthUser> findByEmail(String email);
    // For "Login with Email OR Username"
    Optional<AuthUser> findByUsernameOrEmail(String username, String email);



}