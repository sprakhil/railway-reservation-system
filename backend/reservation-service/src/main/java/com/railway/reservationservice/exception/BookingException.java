package com.railway.reservationservice.exception;

import org.springframework.http.HttpStatus;
public class BookingException extends RuntimeException {
    private final HttpStatus status;
    public BookingException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus getStatus() { return status; }
    public static BookingException badRequest(String message) { return new BookingException(HttpStatus.BAD_REQUEST, message); }
    public static BookingException conflict(String message) { return new BookingException(HttpStatus.CONFLICT, message); }
}
