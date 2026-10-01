package com.railway.reservationservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CancellationResponse {
    private String pnr;
    private String message;
    private java.math.BigDecimal originalFare;
    private java.math.BigDecimal cancellationFee;
    private java.math.BigDecimal refundAmount;
    private String refundStatus;
    private boolean testMode;
    private String currentStatus;
}
