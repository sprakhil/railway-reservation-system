package com.railway.authservice.service;

import com.railway.authservice.config.JwtUtils;
import com.railway.authservice.dto.*;
import com.railway.authservice.entity.AuthUser;
import com.railway.authservice.entity.OtpVerification;
import com.railway.authservice.entity.OtpVerification.Purpose;
import com.railway.authservice.entity.RefreshToken;
import com.railway.authservice.exception.CustomAuthException;
import com.railway.authservice.repository.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final OtpVerificationRepository otpRepository;
    private final OtpEmailService otpEmailService;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${jwt.refreshExpirationMs:604800000}")
    private Long refreshExpirationMs = 604800000L;
    @Value("${auth.bootstrap-admin.enabled:false}")
    private boolean bootstrapAdminEnabled;
    @Value("${auth.bootstrap-admin.username:}")
    private String bootstrapAdminUsername;
    @Value("${auth.bootstrap-admin.email:}")
    private String bootstrapAdminEmail;
    @Value("${auth.bootstrap-admin.password:}")
    private String bootstrapAdminPassword;

    @PostConstruct
    public void initAdmin() {
        if (!bootstrapAdminEnabled) return;
        if (bootstrapAdminUsername == null || !bootstrapAdminUsername.matches("[A-Za-z0-9_]{4,20}")
                || bootstrapAdminEmail == null || !bootstrapAdminEmail.contains("@")) {
            throw new IllegalStateException("Explicit admin username, email and strong password are required when admin bootstrap is enabled.");
        }
        PasswordPolicy.validate(bootstrapAdminPassword);
        if (userRepository.findByUsername(bootstrapAdminUsername).isPresent() || userRepository.existsByPublicUsernameIgnoreCase(bootstrapAdminUsername)
                || userRepository.existsByEmail(normalizeEmail(bootstrapAdminEmail))) return;
        userRepository.save(AuthUser.builder().username(bootstrapAdminUsername).publicUsername(bootstrapAdminUsername.toLowerCase(Locale.ROOT))
                .email(normalizeEmail(bootstrapAdminEmail)).password(passwordEncoder.encode(bootstrapAdminPassword))
                .role(AuthUser.Role.ADMIN).status(AuthUser.Status.ACTIVE).provider(AuthUser.Provider.LOCAL)
                .emailVerified(true).isVerified(true).build());
    }

    @Transactional
    public String registerUser(RegisterRequest req) {
        String email = normalizeEmail(req.getEmail());
        if (userRepository.existsByUsername(req.getUsername()) || userRepository.existsByUsernameIgnoreCase(req.getUsername())
                || userRepository.existsByPublicUsernameIgnoreCase(req.getUsername())) throw new CustomAuthException("Username already taken");
        if (userRepository.existsByEmail(email)) throw new CustomAuthException("Email already registered");
        PasswordPolicy.validate(req.getPassword());
        AuthUser user = AuthUser.builder().username(req.getUsername()).publicUsername(req.getUsername().toLowerCase(Locale.ROOT)).email(email)
                .password(passwordEncoder.encode(req.getPassword())).role(AuthUser.Role.PASSENGER)
                .provider(AuthUser.Provider.LOCAL).isVerified(false).emailVerified(false)
                .status(AuthUser.Status.ACTIVE).build();
        userRepository.save(user);
        issueOtp(email, Purpose.REGISTRATION);
        return "Registration successful. Please verify the OTP sent to your email.";
    }

    // Failed-attempt counters must commit even when an invalid OTP is rejected.
    @Transactional(noRollbackFor = CustomAuthException.class)
    public String verifyOtp(VerifyOtpReq req) {
        AuthUser user = lockedUser(req.getEmail());
        requireActive(user);
        consumeOtp(user.getEmail(), req.getOtp(), Purpose.REGISTRATION);
        user.setVerified(true);
        user.setEmailVerified(true);
        userRepository.save(user);
        events.publishEvent(new WelcomeEmail(user.getEmail(),user.displayUsername()));
        return "Email verified successfully! You can now log in.";
    }

    @Transactional
    public String resendOtp(String email) {
        AuthUser user = lockedUser(email);
        requireActive(user);
        if (user.isVerified() || user.isEmailVerified()) return "Email is already verified.";
        issueOtp(user.getEmail(), Purpose.REGISTRATION);
        return "A new verification OTP has been sent.";
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String identifier = request.getIdentifier().trim();
        AuthUser user = userRepository.findByPublicUsernameIgnoreCase(identifier)
                .flatMap(found -> userRepository.findByUsernameForUpdate(found.getUsername()))
                .or(() -> userRepository.findByUsernameOrEmail(identifier, normalizeEmail(identifier)))
                .orElseThrow(() -> new CustomAuthException("Invalid credentials"));
        if (!identifier.contains("@") && user.getUsernameChangedAt()!=null
                && !identifier.equalsIgnoreCase(user.displayUsername())) throw new CustomAuthException("Invalid credentials");
        if (user.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new CustomAuthException("Invalid credentials");
        }
        requireActiveVerified(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refreshToken(String requestRefreshToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(requestRefreshToken)
                .orElseThrow(() -> new CustomAuthException("Invalid refresh token. Please sign in again."));
        if (refreshToken.getSessionId()==null || !refreshToken.getExpiryDate().isAfter(Instant.now())) {
            throw new CustomAuthException("Refresh token expired. Please sign in again.");
        }
        AuthUser user = refreshToken.getUser();
        requireActiveVerified(user);
        return response(user, refreshToken);
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleOAuthClient.Identity identity) {
        if (!identity.emailVerified() || identity.subject() == null || identity.subject().isBlank()) {
            throw new CustomAuthException("Google must verify your email before sign-in.");
        }
        String email = normalizeEmail(identity.email());
        AuthUser user = userRepository.findByProviderId(identity.subject()).orElse(null);
        if (user == null) user = userRepository.findByEmailForUpdate(email).orElse(null);
        if (user != null) {
            requireActive(user);
            if (user.getProviderId() != null && !user.getProviderId().equals(identity.subject())) {
                throw new CustomAuthException("This account is linked to a different Google identity.");
            }
            if (user.getProviderId() == null) {
                // Google is authoritative for Gmail and verified Workspace domains only.
                // Never let a Google identity take over an unverified local registration.
                boolean authoritative = email.endsWith("@gmail.com")
                        || (identity.hostedDomain() != null && !identity.hostedDomain().isBlank());
                if (!(user.isVerified() || user.isEmailVerified()) || !authoritative) {
                    throw new CustomAuthException("An email account already exists. Sign in with its password first; automatic Google linking is unavailable.");
                }
                user.setProviderId(identity.subject());
            }
            if (!user.getEmail().equalsIgnoreCase(email)) {
                throw new CustomAuthException("Your Google email has changed. Account support is required before sign-in.");
            }
        } else {
            user = AuthUser.builder().username("google_" + UUID.randomUUID().toString().replace("-", "").substring(0, 13))
                    .email(email).role(AuthUser.Role.PASSENGER).status(AuthUser.Status.ACTIVE)
                    .provider(AuthUser.Provider.GOOGLE).providerId(identity.subject()).build();
        }
        boolean newAccount=user.getId()==null;
        user.setEmailVerified(true);
        user.setVerified(true);
        if (user.getPublicUsername()==null) user.setPublicUsername(user.getUsername().toLowerCase(Locale.ROOT));
        user = userRepository.save(user);
        if(newAccount) events.publishEvent(new WelcomeEmail(user.getEmail(),user.displayUsername()));
        return issueTokens(user);
    }

    @Transactional
    public String forgotPassword(String email) {
        AuthUser user = userRepository.findByEmailForUpdate(normalizeEmail(email)).orElse(null);
        if (user != null && user.getStatus() == AuthUser.Status.ACTIVE && user.getPassword() != null
                && (user.isVerified() || user.isEmailVerified())) {
            issueOtp(user.getEmail(), Purpose.PASSWORD_RESET);
        }
        return "If an eligible account exists, a password reset OTP has been sent.";
    }

    @Transactional(noRollbackFor = CustomAuthException.class)
    public String resetPassword(ResetPasswordReq req) {
        PasswordPolicy.validate(req.getNewPassword());
        AuthUser user = lockedUser(req.getEmail());
        requireActiveVerified(user);
        if (user.getPassword() == null) {
            throw new CustomAuthException("Use Google sign-in for this account.");
        }
        consumeOtp(user.getEmail(), req.getOtp(), Purpose.PASSWORD_RESET);
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
        refreshTokenRepository.deleteByUser(user);
        return "Password reset successfully. Please sign in again.";
    }

    private void issueOtp(String email, Purpose purpose) {
        LocalDateTime now = LocalDateTime.now();
        otpRepository.findTopByEmailAndPurposeOrderByExpiryTimeDesc(email, purpose).ifPresent(previous -> {
            if (previous.getCreatedAt().isAfter(now.minusSeconds(60))) {
                throw new CustomAuthException("Wait 60 seconds before requesting another OTP.");
            }
        });
        otpRepository.deleteByEmailAndPurpose(email, purpose);
        String otp = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
        otpRepository.save(OtpVerification.builder().email(email).purpose(purpose)
                .otp(passwordEncoder.encode(otp)).createdAt(now).expiryTime(now.plusMinutes(5)).failedAttempts(0).build());
        if (purpose == Purpose.REGISTRATION) otpEmailService.sendOtpEmail(email, otp);
        else otpEmailService.sendPasswordResetOtp(email, otp);
    }

    private void consumeOtp(String email, String otp, Purpose purpose) {
        OtpVerification record = otpRepository.findTopByEmailAndPurposeOrderByExpiryTimeDesc(email, purpose)
                .orElseThrow(() -> new CustomAuthException("No OTP exists for this action. Request a new OTP."));
        if (!record.getExpiryTime().isAfter(LocalDateTime.now())) throw new CustomAuthException("OTP expired. Request a new OTP.");
        if (record.getFailedAttempts() >= 5) throw new CustomAuthException("Too many incorrect attempts. Request a new OTP.");
        if (otp == null || !otp.matches("\\d{6}") || !passwordEncoder.matches(otp, record.getOtp())) {
            record.setFailedAttempts(record.getFailedAttempts() + 1);
            otpRepository.save(record);
            throw new CustomAuthException("Invalid OTP.");
        }
        otpRepository.deleteByEmailAndPurpose(email, purpose);
    }

    private AuthUser lockedUser(String email) {
        return userRepository.findByEmailForUpdate(normalizeEmail(email))
                .orElseThrow(() -> new CustomAuthException("Account or OTP request not found."));
    }

    private void requireActive(AuthUser user) {
        if (user.getStatus() != AuthUser.Status.ACTIVE) throw new CustomAuthException("Account is not active.");
    }

    private void requireActiveVerified(AuthUser user) {
        requireActive(user);
        // Accept either historical verification flag; verified actions keep both consistent.
        if (!user.isVerified() && !user.isEmailVerified()) throw new CustomAuthException("Verify your email using the registration OTP before signing in.");
    }

    private AuthResponse issueTokens(AuthUser user) {
        RefreshToken token = refreshTokenRepository.save(RefreshToken.builder().user(user)
                .token(UUID.randomUUID().toString()).sessionId(UUID.randomUUID().toString()).expiryDate(Instant.now().plusMillis(refreshExpirationMs)).build());
        return response(user, token);
    }

    private AuthResponse response(AuthUser user, RefreshToken refreshToken) {
        return AuthResponse.builder().accessToken(jwtUtils.generateAccessToken(user,refreshToken.getSessionId())).refreshToken(refreshToken.getToken())
                .username(user.getUsername()).role(user.getRole().name()).displayUsername(user.displayUsername())
                .usernameChangeAllowed(user.getUsernameChangedAt()==null)
                .usernameSetupSuggested(user.getProvider()==AuthUser.Provider.GOOGLE && user.getUsernameChangedAt()==null
                    && user.displayUsername().startsWith("google_")).build();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) throw new CustomAuthException("Email is required.");
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
