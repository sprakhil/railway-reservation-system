package com.railway.reservationservice.service;

import com.railway.reservationservice.dto.CancellationResponse;
import com.railway.reservationservice.entity.Reservation;
import com.railway.reservationservice.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @InjectMocks
    private ReservationService reservationService;

    @Test
    void cancelReservation_Success_CalculatesRefundCorrectly() {
        // Arrange
        String pnr = "PNR12345";
        Reservation mockReservation = new Reservation();
        mockReservation.setPnr(pnr);
        mockReservation.setTotalFare(1000.0);
        mockReservation.setStatus(Reservation.ReservationStatus.CONFIRMED);

        when(reservationRepository.findByPnr(pnr)).thenReturn(Optional.of(mockReservation));

        // Act
        CancellationResponse response = reservationService.cancelReservation(pnr);

        // Assert
        assertNotNull(response);
        assertEquals(1000.0, response.getOriginalFare());
        assertEquals(200.0, response.getCancellationFee()); // 20% deduction
        assertEquals(800.0, response.getRefundAmount());
        assertEquals(Reservation.ReservationStatus.CANCELLED, mockReservation.getStatus());

        verify(reservationRepository, times(1)).save(mockReservation);
    }

    @Test
    void cancelReservation_AlreadyCancelled_ThrowsException() {
        // Arrange
        String pnr = "PNR99999";
        Reservation mockReservation = new Reservation();
        mockReservation.setPnr(pnr);
        mockReservation.setStatus(Reservation.ReservationStatus.CANCELLED);

        when(reservationRepository.findByPnr(pnr)).thenReturn(Optional.of(mockReservation));

        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () -> {
            reservationService.cancelReservation(pnr);
        });

        assertTrue(exception.getMessage().contains("already cancelled"));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void cancelReservation_PnrNotFound_ThrowsException() {
        // Arrange
        String pnr = "INVALID_PNR";
        when(reservationRepository.findByPnr(pnr)).thenReturn(Optional.empty());

        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () -> {
            reservationService.cancelReservation(pnr);
        });

        assertTrue(exception.getMessage().contains("PNR not found"));
        verify(reservationRepository, never()).save(any());
    }
}