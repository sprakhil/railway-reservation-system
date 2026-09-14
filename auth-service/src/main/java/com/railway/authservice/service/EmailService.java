package com.railway.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendRegistrationEmail(String toEmail, String username) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Welcome to Railway Reservation System!");
            message.setText("Hello " + username + ",\n\nYour account has been created successfully. Welcome aboard!");

            mailSender.send(message);
            log.info("Registration email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email to {}. Error: {}", toEmail, e.getMessage());
            // We don't throw an exception here because we don't want the user registration to fail
            // just because the email server is temporarily down.
        }
    }
}