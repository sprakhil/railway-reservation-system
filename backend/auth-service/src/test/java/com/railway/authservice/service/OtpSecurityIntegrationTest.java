package com.railway.authservice.service;

import com.railway.authservice.dto.*;
import com.railway.authservice.entity.AuthUser;
import com.railway.authservice.entity.OtpVerification;
import com.railway.authservice.entity.OtpVerification.Purpose;
import com.railway.authservice.exception.CustomAuthException;
import com.railway.authservice.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.LocalDateTime;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=", "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "spring.cloud.compatibility-verifier.enabled=false", "spring.datasource.url=jdbc:h2:mem:otp-security;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.mail.host=localhost", "jwt.secret=test-only-jwt-secret-with-at-least-32-bytes", "jwt.expirationMs=900000",
        "auth.bootstrap-admin.enabled=false"
})
class OtpSecurityIntegrationTest {
    @Autowired AuthService auth;
    @Autowired AuthUserRepository users;
    @Autowired OtpVerificationRepository otps;
    @Autowired RefreshTokenRepository refresh;
    @Autowired PasswordEncoder passwords;
    @MockitoBean OtpEmailService mail;
    @MockitoBean org.springframework.mail.javamail.JavaMailSender smtp;
    @org.junit.jupiter.api.BeforeEach void prepareWelcomeMail(){lenient().when(smtp.createMimeMessage()).thenAnswer(i->new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties())));}

    private AuthUser user(boolean verified) {
        String name = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return users.save(AuthUser.builder().username(name).email(name + "@example.test")
                .password(passwords.encode("Password123!"))
                .provider(AuthUser.Provider.LOCAL).role(AuthUser.Role.PASSENGER).status(AuthUser.Status.ACTIVE)
                .isVerified(verified).emailVerified(verified).build());
    }

    private void otp(AuthUser user, Purpose purpose, LocalDateTime expiry) {
        otps.save(OtpVerification.builder().email(user.getEmail()).purpose(purpose)
                .otp(passwords.encode("123456")).createdAt(LocalDateTime.now().minusMinutes(1))
                .expiryTime(expiry).failedAttempts(0).build());
    }

    private VerifyOtpReq verifyReq(AuthUser user, String value) {
        var req = new VerifyOtpReq(); req.setEmail(user.getEmail()); req.setOtp(value); return req;
    }

    private ResetPasswordReq resetReq(AuthUser user) {
        var req = new ResetPasswordReq(); req.setEmail(user.getEmail()); req.setOtp("123456");
        req.setNewPassword("Changed123!"); return req;
    }

    @Test void registrationStoresOnlyHashedPurposeScopedOtpAndThrottlesResend() {
        var req = new RegisterRequest(); req.setUsername("new_" + UUID.randomUUID().toString().substring(0, 8));
        req.setEmail(req.getUsername() + "@example.test"); req.setPassword("Password123!");
        auth.registerUser(req);
        var code = ArgumentCaptor.forClass(String.class);
        verify(mail).sendOtpEmail(eq(req.getEmail()), code.capture());
        var stored = otps.findTopByEmailAndPurposeOrderByExpiryTimeDesc(req.getEmail(), Purpose.REGISTRATION).orElseThrow();
        assertTrue(code.getValue().matches("\\d{6}"));
        assertNotEquals(code.getValue(), stored.getOtp());
        assertTrue(passwords.matches(code.getValue(), stored.getOtp()));
        assertThrows(CustomAuthException.class, () -> auth.resendOtp(req.getEmail()));
        verify(mail, times(1)).sendOtpEmail(eq(req.getEmail()), anyString());
    }

    @Test void incorrectAttemptsCommitAndLockOutEvenTheCorrectCodeAfterFiveFailures() {
        var user = user(false); otp(user, Purpose.REGISTRATION, LocalDateTime.now().plusMinutes(5));
        for (int i = 1; i <= 5; i++) {
            assertThrows(CustomAuthException.class, () -> auth.verifyOtp(verifyReq(user, "999999")));
            assertEquals(i, otps.findTopByEmailAndPurposeOrderByExpiryTimeDesc(user.getEmail(), Purpose.REGISTRATION).orElseThrow().getFailedAttempts());
        }
        assertThrows(CustomAuthException.class, () -> auth.verifyOtp(verifyReq(user, "123456")));
        assertFalse(users.findById(user.getId()).orElseThrow().isVerified());
    }

    @Test void verifiedOtpIsConsumedOnceAndSetsBothFlags() {
        var user = user(false); otp(user, Purpose.REGISTRATION, LocalDateTime.now().plusMinutes(5));
        auth.verifyOtp(verifyReq(user, "123456"));
        var saved = users.findById(user.getId()).orElseThrow();
        assertTrue(saved.isVerified()); assertTrue(saved.isEmailVerified());
        verify(smtp,times(1)).send(any(jakarta.mail.internet.MimeMessage.class));
        assertThrows(CustomAuthException.class, () -> auth.verifyOtp(verifyReq(user, "123456")));
    }

    @Test void expiredOtpCannotVerifyAnAccount() {
        var user = user(false); otp(user, Purpose.REGISTRATION, LocalDateTime.now().minusSeconds(1));
        assertThrows(CustomAuthException.class, () -> auth.verifyOtp(verifyReq(user, "123456")));
        assertFalse(users.findById(user.getId()).orElseThrow().isEmailVerified());
        verify(smtp,never()).send(any(jakarta.mail.internet.MimeMessage.class));
    }

    @Test void registrationOtpCannotResetAPassword() {
        var user = user(true); otp(user, Purpose.REGISTRATION, LocalDateTime.now().plusMinutes(5));
        assertThrows(CustomAuthException.class, () -> auth.resetPassword(resetReq(user)));
        assertTrue(passwords.matches("Password123!", users.findById(user.getId()).orElseThrow().getPassword()));
        assertTrue(otps.findTopByEmailAndPurposeOrderByExpiryTimeDesc(user.getEmail(), Purpose.REGISTRATION).isPresent());
    }

    @Test void successfulResetRevokesRefreshTokenAndConsumesOnlyPasswordResetOtp() {
        var user = user(true);
        var login = new LoginRequest(); login.setIdentifier(user.getEmail()); login.setPassword("Password123!");
        String token = auth.login(login).getRefreshToken();
        otp(user, Purpose.REGISTRATION, LocalDateTime.now().plusMinutes(5));
        otp(user, Purpose.PASSWORD_RESET, LocalDateTime.now().plusMinutes(5));
        auth.resetPassword(resetReq(user));
        assertTrue(refresh.findByToken(token).isEmpty());
        assertThrows(CustomAuthException.class, () -> auth.refreshToken(token));
        assertTrue(passwords.matches("Changed123!", users.findById(user.getId()).orElseThrow().getPassword()));
        assertTrue(otps.findTopByEmailAndPurposeOrderByExpiryTimeDesc(user.getEmail(), Purpose.PASSWORD_RESET).isEmpty());
        assertTrue(otps.findTopByEmailAndPurposeOrderByExpiryTimeDesc(user.getEmail(), Purpose.REGISTRATION).isPresent());
    }

    @Test void failedResetAttemptsCommitWithoutChangingPasswordOrRefreshToken() {
        var user = user(true);
        var login = new LoginRequest(); login.setIdentifier(user.getEmail()); login.setPassword("Password123!");
        String token = auth.login(login).getRefreshToken();
        otp(user, Purpose.PASSWORD_RESET, LocalDateTime.now().plusMinutes(5));
        var req = resetReq(user); req.setOtp("654321");
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThrows(CustomAuthException.class, () -> auth.resetPassword(req));
            assertEquals(attempt, otps.findTopByEmailAndPurposeOrderByExpiryTimeDesc(user.getEmail(), Purpose.PASSWORD_RESET).orElseThrow().getFailedAttempts());
        }
        assertThrows(CustomAuthException.class, () -> auth.resetPassword(resetReq(user)));
        assertTrue(passwords.matches("Password123!", users.findById(user.getId()).orElseThrow().getPassword()));
        assertTrue(refresh.findByToken(token).isPresent());
    }

    @Test void googleOnlyAccountCannotAcquirePasswordViaAStaleResetOtp() {
        var user = user(true); user.setPassword(null); user.setProvider(AuthUser.Provider.GOOGLE);
        users.save(user);
        otp(user, Purpose.PASSWORD_RESET, LocalDateTime.now().plusMinutes(5));
        assertThrows(CustomAuthException.class, () -> auth.resetPassword(resetReq(user)));
        assertNull(users.findById(user.getId()).orElseThrow().getPassword());
    }
    @Test void rolledBackVerificationDoesNotSendWelcome() {
        var user=user(false);otp(user,Purpose.REGISTRATION,LocalDateTime.now().plusMinutes(5));
        new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{
            auth.verifyOtp(verifyReq(user,"123456"));status.setRollbackOnly();return null;
        });
        verify(smtp,never()).send(any(jakarta.mail.internet.MimeMessage.class));
        assertFalse(users.findById(user.getId()).orElseThrow().isVerified());
    }
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Test void newGoogleAccountGetsOneWelcomeButReturningLoginDoesNot() {
        String id=UUID.randomUUID().toString();
        var identity=new GoogleOAuthClient.Identity(id,id+"@gmail.com",true,null);
        auth.loginWithGoogle(identity);auth.loginWithGoogle(identity);
        verify(smtp,times(1)).send(any(jakarta.mail.internet.MimeMessage.class));
    }
}
