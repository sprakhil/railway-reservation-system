package com.railway.paymentservice.dto;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentOrderReq {
    @NotBlank(message = "PNR is required")
    @Pattern(regexp = "[A-Za-z0-9]{1,10}", message = "Invalid PNR")
    private String pnr;
    // Reject old clients that try to choose their own fare.
    @Null(message = "Do not supply amount; the saved booking fare is authoritative")
    private BigDecimal amount;
}
