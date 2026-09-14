package com.railway.authservice.service;

import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.railway.authservice.config.JwtUtils;
import com.railway.authservice.dto.*;
import com.railway.authservice.entity.AuthUser;
import com.railway.authservice.entity.OtpVerification;
import com.railway.authservice.entity.RefreshToken;
import com.railway.authservice.exception.CustomAuthException;
import com.railway.authservice.repository.AuthUserRepository;
import com.railway.authservice.repository.OtpVerificationRepository;
import com.railway.authservice.repository.RefreshTokenRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final EmailService emailService;
    private final OtpVerificationRepository otpRepository;
    private final OtpEmailService otpEmailService;

    @Value("${jwt.refreshExpirationMs}")
    private Long refreshExpirationMs; // 7 Days in MS


    @Value("${google.client.id}")
    private String googleClientId;

    @Value("${google.client.secret}")
    private String googleClientSecret;

    @Value("${google.redirect.uri}")
    private String googleRedirectUri;


    // Creates default ADMIN if it doesn't exist
    @PostConstruct
    public void initAdmin() {
        if (userRepository.findByUsername("admin").isEmpty()) {
            AuthUser admin = AuthUser.builder()
                    .username("admin")
                    .email("benstokes.cap@gmail.com")
                    .password(passwordEncoder.encode("admin123"))
                    .role(AuthUser.Role.ADMIN)
                    .status(AuthUser.Status.ACTIVE)
                    .provider(AuthUser.Provider.LOCAL)
                    .emailVerified(true)
                    .isVerified(true)
                    .build();
            userRepository.save(admin);
            log.info("Default Admin account created.");
        }
    }

    @Transactional
    public String registerUser(RegisterRequest req) {
        // 1. Check if username or email already exists
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new RuntimeException("Username already taken");
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // 2. Save user with isVerified = false
        AuthUser user = AuthUser.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .role(AuthUser.Role.PASSENGER)
                .isVerified(false)
                .status(AuthUser.Status.ACTIVE)
                .build();
        userRepository.save(user);

        // 3. Generate 6-digit OTP & 5-minute expiry
        String otp = String.format("%06d", new java.util.Random().nextInt(1000000));
        OtpVerification otpRecord = OtpVerification.builder()
                .email(req.getEmail())
                .otp(otp)
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .build();
        otpRepository.save(otpRecord);

        // 4. Send the OTP email
        otpEmailService.sendOtpEmail(req.getEmail(), otp);

        return "Registration successful. Please verify the OTP sent to your email.";
    }

    @Transactional
    public String verifyOtp(VerifyOtpReq req) {
        OtpVerification verification = otpRepository.findTopByEmailOrderByExpiryTimeDesc(req.getEmail())
                .orElseThrow(() -> new RuntimeException("No OTP found for this email"));

        if (verification.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("OTP has expired. Please request a new one.");
        }

        if (!verification.getOtp().equals(req.getOtp())) {
            throw new RuntimeException("Invalid OTP. Please try again.");
        }

        // Mark user as verified
        AuthUser user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setVerified(true);
        userRepository.save(user);

        // Clean up used OTP
        otpRepository.deleteByEmail(req.getEmail());

        return "Email verified successfully! You can now log in.";
    }

    @Transactional
    public String resendOtp(String email) {
        AuthUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        if (user.isVerified()) {
            return "Email is already verified.";
        }

        // Clean previous OTPs
        otpRepository.deleteByEmail(email);

        String otp = String.format("%06d", new java.util.Random().nextInt(1000000));
        OtpVerification otpRecord = OtpVerification.builder()
                .email(email)
                .otp(otp)
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .build();
        otpRepository.save(otpRecord);

        otpEmailService.sendOtpEmail(email, otp);
        return "New OTP sent to " + email;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // 1. Get the input
        String identifier = request.getIdentifier();

        // 2. Search both columns simultaneously
        AuthUser user = userRepository.findByUsernameOrEmail(identifier, identifier)
                .orElseThrow(() -> new RuntimeException("Invalid credentials: User not found"));

        // 3. Validate Password (CRITICAL step)
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials: Wrong password");
        }

        // 4. Verification and Status Checks
        if (!user.isVerified()) {
            throw new RuntimeException("Email not verified! Please verify your account with the OTP sent to your email.");
        }

        if (user.getStatus() != AuthUser.Status.ACTIVE) {
            throw new CustomAuthException("Account is " + user.getStatus().name());
        }

        // 5. Generate Tokens
        String accessToken = jwtUtils.generateAccessToken(user);
        RefreshToken refreshToken = createRefreshToken(user);

        // 6. Return Response
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }

    private RefreshToken createRefreshToken(AuthUser user) {
        // Delete old token if exists
        refreshTokenRepository.deleteByUser(user);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(refreshExpirationMs))
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public AuthResponse refreshToken(String requestRefreshToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(requestRefreshToken)
                .orElseThrow(() -> new CustomAuthException("Refresh token is not in database!"));

        if (refreshToken.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(refreshToken);
            throw new CustomAuthException("Refresh token was expired. Please make a new signin request");
        }

        AuthUser user = refreshToken.getUser();
        String newAccessToken = jwtUtils.generateAccessToken(user);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken.getToken())
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleAuthCodeReq request) {
        try {
            String decodedCode = java.net.URLDecoder.decode(request.getCode(), java.nio.charset.StandardCharsets.UTF_8);
            // 1. Exchange the Authorization Code for Tokens using Client ID and Secret
            GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                    new NetHttpTransport(),
                    new GsonFactory(),
                    googleClientId,
                    googleClientSecret,
                    decodedCode,
                    googleRedirectUri
            ).execute();

            // 2. Extract and Verify the ID Token
            String idTokenString = tokenResponse.getIdToken();
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new CustomAuthException("Invalid Google ID token signature.");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String googleId = payload.getSubject();

            // 3. Register or Login User
            Optional<AuthUser> userOptional = userRepository.findByEmail(email);
            AuthUser user;

            if (userOptional.isPresent()) {
                user = userOptional.get();
                if (user.getStatus() != AuthUser.Status.ACTIVE) {
                    throw new CustomAuthException("Account is " + user.getStatus().name());
                }
            } else {
                user = AuthUser.builder()
                        .username(email.split("@")[0] + "_" + googleId.substring(0, 5))
                        .email(email)
                        .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                        .role(AuthUser.Role.PASSENGER)
                        .status(AuthUser.Status.ACTIVE)
                        .provider(AuthUser.Provider.GOOGLE)
                        .providerId(googleId)
                        .emailVerified(payload.getEmailVerified())
                        .build();
                user = userRepository.save(user);
            }

            // 4. Generate our System's JWTs
            String accessToken = jwtUtils.generateAccessToken(user);
            RefreshToken refreshToken = createRefreshToken(user);

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken.getToken())
                    .username(user.getUsername())
                    .role(user.getRole().name())
                    .build();

        } catch (TokenResponseException e) {
            // Check if Google sent back "invalid_grant"
            if (e.getDetails() != null && "invalid_grant".equals(e.getDetails().getError())) {
                throw new CustomAuthException("The Google login code has expired or was already used. Please log in again.");
            }
            throw new CustomAuthException("Google authentication failed: " + e.getMessage());
        }
        catch (Exception e) {
            log.error("Google authentication failed", e);
            throw new CustomAuthException("Google authentication failed: " + e.getMessage());
        }
    }

    @Transactional
    public String forgotPassword(String email) {
        // 1. Verify user exists
        AuthUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No account found with that email address"));

        // 2. Clean previous OTPs for this email to prevent spam/confusion
        otpRepository.deleteByEmail(email);

        // 3. Generate new 6-digit OTP
        String otp = String.format("%06d", new java.util.Random().nextInt(1000000));
        OtpVerification otpRecord = OtpVerification.builder()
                .email(email)
                .otp(otp)
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .build();
        otpRepository.save(otpRecord);

        // 4. Dispatch Email
        otpEmailService.sendPasswordResetOtp(email, otp);

        return "Password reset OTP sent to " + email;
    }

    @Transactional
    public String resetPassword(ResetPasswordReq req) {
        // 1. Find the latest OTP
        OtpVerification verification = otpRepository.findTopByEmailOrderByExpiryTimeDesc(req.getEmail())
                .orElseThrow(() -> new RuntimeException("No OTP request found for this email"));

        // 2. Validate Time and Value
        if (verification.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("OTP has expired. Please request a new one.");
        }
        if (!verification.getOtp().equals(req.getOtp())) {
            throw new RuntimeException("Invalid OTP. Please try again.");
        }

        // 3. Update the Password
        AuthUser user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        // 4. Clean up used OTP so it cannot be used again
        otpRepository.deleteByEmail(req.getEmail());

        return "Password reset successfully. You can now log in with your new password.";
    }

}