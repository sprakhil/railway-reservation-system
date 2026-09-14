package com.railway.notificationservice.service;

import com.railway.notificationservice.dto.NotificationEvent;
import com.railway.notificationservice.entity.NotificationLog;
import com.railway.notificationservice.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final NotificationLogRepository logRepository;

    public void processTicketConfirmation(NotificationEvent event) {
        log.info("Processing ticket confirmation email for PNR: {}", event.getPnr());

        NotificationLog auditLog = NotificationLog.builder()
                .pnr(event.getPnr())
                .recipientEmail(event.getUserEmail())
                .sentAt(LocalDateTime.now())
                .build();

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(event.getUserEmail());
            message.setSubject("Ticket Confirmation - PNR: " + event.getPnr());

            String body = String.format(
                    "Dear %s,\n\nYour ticket has been successfully booked!\n\n" +
                            "PNR Number: %s\nTrain Number: %s\nDate of Journey: %s\n\n" +
                            "Thank you for traveling with us.",
                    event.getUsername(), event.getPnr(), event.getTrainNumber(), event.getDateOfJourney()
            );

            message.setText(body);
            mailSender.send(message);

            auditLog.setStatus(NotificationLog.NotificationStatus.SUCCESS);
            log.info("Email sent successfully to {}", event.getUserEmail());

        } catch (Exception e) {
            log.error("Failed to send email to {}", event.getUserEmail(), e);
            auditLog.setStatus(NotificationLog.NotificationStatus.FAILED);
            auditLog.setErrorMessage(e.getMessage());
        }

        // Save audit log to DB
        logRepository.save(auditLog);
    }
}