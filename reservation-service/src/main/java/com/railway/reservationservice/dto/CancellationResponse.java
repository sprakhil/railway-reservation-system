package com.railway.reservationservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CancellationResponse {
    private String pnr;
    private String message;
    private double originalFare;
    private double cancellationFee;
    private double refundAmount;
    private String currentStatus;
}