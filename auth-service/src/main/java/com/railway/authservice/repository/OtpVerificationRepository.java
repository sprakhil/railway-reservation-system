package com.railway.authservice.repository;

import com.railway.authservice.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {
    Optional<OtpVerification> findTopByEmailOrderByExpiryTimeDesc(String email);

    @Transactional
    void deleteByEmail(String email);
}