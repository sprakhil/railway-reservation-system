package com.railway.scheduleservice.exception;

/** Only these deliberately authored route messages are safe to expose to the caller. */
public class ScheduleValidationException extends IllegalArgumentException {
    public ScheduleValidationException(String message) { super(message); }
}
