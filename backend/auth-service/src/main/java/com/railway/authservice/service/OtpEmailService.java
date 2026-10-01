package com.railway.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor @Slf4j
public class OtpEmailService {
    private final JavaMailSender mailSender;
    @Value("${app.frontend.base-url:http://localhost:4200}") private String frontend="http://localhost:4200";
    @Value("${app.mail.from:${spring.mail.username:}}") private String from="";
    public void sendOtpEmail(String recipientEmail,String otp) { send(recipientEmail,otp,false); }
    public void sendPasswordResetOtp(String recipientEmail,String otp) { send(recipientEmail,otp,true); }
    private void send(String recipientEmail,String otp,boolean reset) {
        try {
            var content=AccountEmailTemplate.otp(otp,reset,frontend);
            var message=mailSender.createMimeMessage();var helper=new MimeMessageHelper(message,true,"UTF-8");
            if(!from.isBlank())helper.setFrom(from,"Railwize");
            helper.setTo(recipientEmail);helper.setSubject(content.subject());helper.setText(content.plain(),content.html());
            RailwizeMail.logo(helper);mailSender.send(message);
        } catch(Exception ex) {
            log.warn("{} code email delivery failed ({})",reset?"Password-reset":"Registration",ex.getClass().getSimpleName());
            throw new RuntimeException(reset?"Could not send password reset email. Please try again.":"Could not send verification email. Please check the email address.");
        }
    }
}
