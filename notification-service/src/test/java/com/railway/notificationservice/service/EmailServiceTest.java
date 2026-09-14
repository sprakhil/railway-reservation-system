package com.railway.notificationservice.service;

import com.railway.notificationservice.dto.NotificationEvent;
import com.railway.notificationservice.entity.NotificationLog;
import com.railway.notificationservice.repository.NotificationLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private NotificationLogRepository logRepository;

    @InjectMocks
    private EmailService emailService;

    @Test
    void processTicketConfirmation_Success_SavesLogAsSuccess() {
        // 1. Arrange: Create the mock event received from RabbitMQ/Kafka
        NotificationEvent event = NotificationEvent.builder()
                .pnr("PNR1234567")
                .userEmail("passenger@railway.com")
                .username("John Doe")
                .trainNumber("12045")
                .dateOfJourney("2026-10-15")
                .build();

        // Simulate the mail sender working perfectly
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        // Simulate the DB save
        when(logRepository.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 2. Act
        emailService.processTicketConfirmation(event);

        // 3. Assert: Intercept the email to verify its contents
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(messageCaptor.capture());

        SimpleMailMessage sentEmail = messageCaptor.getValue();
        assertEquals("passenger@railway.com", sentEmail.getTo()[0]);
        assertTrue(sentEmail.getSubject().contains("PNR1234567"));

        // Intercept the database log to ensure it was marked SUCCESS
        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository, times(1)).save(logCaptor.capture());

        NotificationLog savedLog = logCaptor.getValue();
        assertEquals(NotificationLog.NotificationStatus.SUCCESS, savedLog.getStatus());
        assertEquals("PNR1234567", savedLog.getPnr());
        assertNull(savedLog.getErrorMessage()); // No error message on success
    }

    @Test
    void processTicketConfirmation_MailServerDown_SavesLogAsFailed() {
        // 1. Arrange
        NotificationEvent event = NotificationEvent.builder()
                .pnr("PNR9999999")
                .userEmail("error@railway.com")
                .build();

        // Simulate the mail server crashing/throwing an exception
        doThrow(new RuntimeException("SMTP server connection timeout"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        when(logRepository.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 2. Act: Call the method. (It shouldn't crash the app because you have a try-catch block!)
        emailService.processTicketConfirmation(event);

        // 3. Assert: Verify it caught the exception and logged it as FAILED
        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository, times(1)).save(logCaptor.capture());

        NotificationLog savedLog = logCaptor.getValue();
        assertEquals(NotificationLog.NotificationStatus.FAILED, savedLog.getStatus());
        assertEquals("SMTP server connection timeout", savedLog.getErrorMessage());
    }
}