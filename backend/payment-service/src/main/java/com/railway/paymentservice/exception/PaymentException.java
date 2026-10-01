package com.railway.paymentservice.exception;
import lombok.Getter;
import org.springframework.http.HttpStatus;
@Getter
public class PaymentException extends RuntimeException {
    private final HttpStatus status;
    public PaymentException(HttpStatus status, String message) { super(message); this.status = status; }
}
