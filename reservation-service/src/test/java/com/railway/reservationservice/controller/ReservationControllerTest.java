package com.railway.reservationservice.controller;

import com.railway.reservationservice.dto.CancellationResponse;
import com.railway.reservationservice.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationControllerTest {

    @Mock
    private ReservationService reservationService;

    @InjectMocks
    private ReservationController reservationController;

    @Test
    void cancelTicket_ShouldReturn200AndReceipt() {
        // Arrange
        String pnr = "PNR12345";

        CancellationResponse mockResponse = CancellationResponse.builder()
                .pnr(pnr)
                .message("Ticket cancelled successfully.")
                .originalFare(1000.0)
                .cancellationFee(200.0)
                .refundAmount(800.0)
                .currentStatus("CANCELLED")
                .build();

        when(reservationService.cancelReservation(pnr)).thenReturn(mockResponse);

        // Act
        ResponseEntity<CancellationResponse> response = reservationController.cancelTicket(pnr);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(pnr, response.getBody().getPnr());
        assertEquals(800.0, response.getBody().getRefundAmount());
        assertEquals("CANCELLED", response.getBody().getCurrentStatus());
    }

    @Test
    void cancelTicket_WhenPnrNotFound_ShouldThrowException() {
        // Arrange
        String fakePnr = "INVALID_123";
        when(reservationService.cancelReservation(fakePnr))
                .thenThrow(new RuntimeException("PNR not found: " + fakePnr));

        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () -> {
            reservationController.cancelTicket(fakePnr);
        });

        assertTrue(exception.getMessage().contains("PNR not found"));
    }

    @Test
    void cancelTicket_WhenAlreadyCancelled_ShouldThrowException() {
        // Arrange
        String pnr = "PNR12345";
        when(reservationService.cancelReservation(pnr))
                .thenThrow(new RuntimeException("Ticket with PNR " + pnr + " is already cancelled."));

        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () -> {
            reservationController.cancelTicket(pnr);
        });

        assertEquals("Ticket with PNR PNR12345 is already cancelled.", exception.getMessage());
    }
}