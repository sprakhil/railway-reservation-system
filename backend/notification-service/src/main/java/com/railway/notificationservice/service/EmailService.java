package com.railway.notificationservice.service;
import com.railway.notificationservice.dto.NotificationEvent;
import com.railway.notificationservice.entity.NotificationLog;
import com.railway.notificationservice.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import java.time.*;

@Service @RequiredArgsConstructor @Slf4j
public class EmailService {
    private final JavaMailSender mailSender;
    private final NotificationLogRepository logRepository;
    @Value("${app.frontend.base-url:http://localhost:4200}") private String frontend="http://localhost:4200";
    @Value("${app.mail.from:${spring.mail.username:}}") private String from="";
    @Value("${notification.refund-timeline:Test refunds are reconciled approximately every 30 seconds while services are running. Provider delays may take longer; there is no bank-credit timeline in test mode.}")
    private String timeline="Test refunds are reconciled approximately every 30 seconds while services are running. Provider delays may take longer; there is no bank-credit timeline in test mode.";
    public boolean processTicketConfirmation(NotificationEvent event) {
        NotificationLog audit=NotificationLog.builder().pnr(event.getPnr()).supportTicketId(event.getSupportTicketId()).recipientEmail(event.getUserEmail()).sentAt(LocalDateTime.now()).build();
        try {
            var content=event.getSupportTicketId()!=null?SupportEmailTemplate.render(event,frontend):BookingEmailTemplate.render(event,frontend,timeline,Instant.now());
            var message=mailSender.createMimeMessage();var helper=new MimeMessageHelper(message,true,"UTF-8");
            if(!from.isBlank())helper.setFrom(from,"Railwize");
            helper.setTo(event.getUserEmail());helper.setSubject(content.subject());helper.setText(content.plain(),content.html());
            RailwizeMail.logo(helper);
            if(event.getTicketPdf()!=null && event.getTicketPdf().length>0)
                helper.addAttachment("Railwize-"+event.getPnr().replaceAll("[^A-Za-z0-9_-]","_")+".pdf",new ByteArrayResource(event.getTicketPdf()),"application/pdf");
            mailSender.send(message);audit.setStatus(NotificationLog.NotificationStatus.SUCCESS);
        } catch(Exception ex) {
            log.warn("Email delivery will retry for event {} ({})",event.getEventId(),ex.getClass().getSimpleName());
            audit.setStatus(NotificationLog.NotificationStatus.FAILED);audit.setErrorMessage(ex.getMessage());
        }
        logRepository.save(audit);return audit.getStatus()==NotificationLog.NotificationStatus.SUCCESS;
    }
}
