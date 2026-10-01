package com.railway.authservice.repository;

import com.railway.authservice.entity.AuthUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface AuthUserRepository extends JpaRepository<AuthUser, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<AuthUser> {
    boolean existsByUsername(String username);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByPublicUsernameIgnoreCase(String username);
    Optional<AuthUser> findByPublicUsernameIgnoreCase(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AuthUser u where u.username = :username")
    Optional<AuthUser> findByUsernameForUpdate(@Param("username") String username);
    boolean existsByEmail(@Email(message = "Invalid email format") @NotBlank(message = "Email is required") String email);

    Optional<AuthUser> findByUsername(String username);
    Optional<AuthUser> findByEmail(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthUser> findByProviderId(String providerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AuthUser u where u.email = :email")
    Optional<AuthUser> findByEmailForUpdate(@Param("email") String email);
    // For "Login with Email OR Username"
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthUser> findByUsernameOrEmail(String username, String email);



}
