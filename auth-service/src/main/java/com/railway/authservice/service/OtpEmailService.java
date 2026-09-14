package com.railway.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpEmailService {

    private final JavaMailSender mailSender;

    public void sendOtpEmail(String recipientEmail, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipientEmail);
            message.setSubject("Your Railway App Registration OTP");
            message.setText("Welcome to Railway Reservation System!\n\n" +
                    "Your One-Time Password (OTP) for email verification is: " + otp + "\n\n" +
                    "This code will expire in 5 minutes. Do not share it with anyone.");

            mailSender.send(message);
            log.info("OTP successfully sent to {}", recipientEmail);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}", recipientEmail, e);
            throw new RuntimeException("Could not send verification email. Please check the email address.");
        }
    }

    public void sendPasswordResetOtp(String recipientEmail, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipientEmail);
            message.setSubject("Password Reset Request - Railway App");
            message.setText("We received a request to reset your password.\n\n" +
                    "Your Password Reset OTP is: " + otp + "\n\n" +
                    "This code will expire in 5 minutes. If you did not request this, please ignore this email.");

            mailSender.send(message);
            log.info("Password Reset OTP successfully sent to {}", recipientEmail);
        } catch (Exception e) {
            log.error("Failed to send Password Reset OTP to {}", recipientEmail, e);
            throw new RuntimeException("Could not send password reset email. Please try again.");
        }
    }
}