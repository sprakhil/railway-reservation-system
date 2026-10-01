package com.railway.paymentservice.client;
import com.railway.paymentservice.dto.*;
import java.math.BigDecimal;
public interface ReservationClient {
    ReservationPaymentContext context(String pnr);
    PaymentConfirmationResult confirm(String pnr, PaymentConfirmation confirmation);
    void paymentFailed(String pnr);
    void refundStatus(String pnr, String paymentId, String status, BigDecimal amount);
}
