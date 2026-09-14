package com.railway.notificationservice.listener;

import com.railway.notificationservice.dto.NotificationEvent;
import com.railway.notificationservice.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final EmailService emailService;

    // Constantly listens to the queue defined in properties
    @RabbitListener(queues = "${rabbitmq.queue.name}")
    public void consumeMessage(NotificationEvent event) {
        log.info("Received Message from RabbitMQ for PNR: {}", event.getPnr());

        // Pass event to Email Service
        emailService.processTicketConfirmation(event);
    }
}