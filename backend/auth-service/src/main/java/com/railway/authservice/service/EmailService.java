package com.railway.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Service @RequiredArgsConstructor @Slf4j
public class EmailService {
    private final JavaMailSender mailSender;
    @Value("${app.frontend.base-url:http://localhost:4200}") private String frontend="http://localhost:4200";
    @Value("${app.mail.from:${spring.mail.username:}}") private String from="";
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void verified(WelcomeEmail event) { sendRegistrationEmail(event.email(),event.username()); }
    public void sendRegistrationEmail(String toEmail,String username) {
        try {
            var message=mailSender.createMimeMessage();
            var helper=new MimeMessageHelper(message,true,"UTF-8");
            if(!from.isBlank()) helper.setFrom(from,"Railwize");
            helper.setTo(toEmail); helper.setSubject("Welcome aboard, your Railwize account is active");
            String url=frontend.replaceAll("/+$","")+"/auth/login";
            String intro="Hello "+username+", your email is verified and your account is ready.";
            String content="<div style='padding:20px;background:#edf3ff;border-radius:10px'><h2 style='font-size:18px;margin:0 0 12px'>Make room for your next journey</h2><p style='line-height:1.8;margin:0'>Search train routes and fares.<br>Save your passengers for faster booking.<br>Find every ticket and booking update in My journeys.</p></div>";
            helper.setText(intro+"\n\nSearch trains, save passengers and manage your bookings.\nSign in: "+url+"\n\nRailwize",RailwizeMail.page("Welcome aboard.",intro,content,url,"Explore Railwize","You can sign in with your verified account. Never share your password or verification codes."));
            RailwizeMail.logo(helper); mailSender.send(message);
        } catch(Exception ex) { log.warn("Welcome email delivery failed ({})",ex.getClass().getSimpleName()); }
    }
}
