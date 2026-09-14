package com.railway.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

// Serializable is required for RabbitMQ message conversion
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent implements Serializable {
    private String pnr;
    private String userEmail; // We will use this to send the email
    private String username;
    private String trainNumber;
    private String dateOfJourney;
}