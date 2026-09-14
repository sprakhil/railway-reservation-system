package com.railway.authservice.repository;

import com.railway.authservice.entity.AuthUser;
import com.railway.authservice.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByUser(AuthUser user); // To clear old tokens on re-login
}