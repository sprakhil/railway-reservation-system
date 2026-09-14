package com.railway.reservationservice.service;

import com.railway.reservationservice.client.PnrServiceClient;
import com.railway.reservationservice.dto.*;
import com.railway.reservationservice.entity.Passenger;
import com.railway.reservationservice.entity.Reservation;
import com.railway.reservationservice.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final PnrServiceClient pnrServiceClient;
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange.name}")
    private String exchangeName;

    @Value("${rabbitmq.routing.key}")
    private String routingKey;

    @Transactional
    @CircuitBreaker(name = "pnrService", fallbackMethod = "bookTicketFallback")
    public ReservationRes bookTicket(String username, String email, ReservationReq req) {

        // 1. Synchronously call the PNR Service via OpenFeign
        String generatedPnr;
        try {
            generatedPnr = pnrServiceClient.generatePnr();
        } catch (Exception e) {
            log.error("Failed to communicate with PNR service", e);
            throw new RuntimeException("PNR Generation failed. Booking cannot proceed.");
        }

        // 2. Create Reservation Entity
        Reservation reservation = Reservation.builder()
                .pnr(generatedPnr)
                .username(username)
                .trainNumber(req.getTrainNumber())
                .dateOfJourney(req.getDateOfJourney())
                .bookingTime(LocalDateTime.now())
                // In a full implementation, status depends on Seat Availability logic
                .status(Reservation.ReservationStatus.CONFIRMED)
                .passengers(new java.util.ArrayList<>())
                .build();

        // 3. Attach Passengers
        req.getPassengers().forEach(pDto -> {
            Passenger passenger = Passenger.builder()
                    .name(pDto.getName())
                    .age(pDto.getAge())
                    .gender(pDto.getGender())
                    .birthPreference(pDto.getBirthPreference())
                    // Hardcoded dummy seat for now
                    .seatNumber("S1-12")
                    .bookingStatus(Passenger.BookingStatus.CONFIRMED)
                    .build();
            reservation.addPassenger(passenger);
        });

        // 4. Save entire transaction to Database
        Reservation savedReservation = reservationRepository.save(reservation);
        log.info("Successfully booked ticket. PNR: {}", savedReservation.getPnr());

        try {
            NotificationEvent event = NotificationEvent.builder()
                    .pnr(savedReservation.getPnr())
                    .username(username)
                    .userEmail(email) // Email fetched from User Service
                    .trainNumber(savedReservation.getTrainNumber())
                    .dateOfJourney(savedReservation.getDateOfJourney().toString())
                    .build();

            // Publish to RabbitMQ
            rabbitTemplate.convertAndSend(exchangeName, routingKey, event);
            log.info("Notification event sent to RabbitMQ for PNR: {}", event.getPnr());
        } catch (Exception e) {
            // We catch the error so if RabbitMQ is down, the booking STILL succeeds!
            log.error("Failed to send notification event", e);
        }

        return mapToDto(savedReservation);
    }

    // --- FALLBACK METHOD ---
    public ReservationRes bookTicketFallback(String username, String email, ReservationReq req, Throwable t) {
        log.error("Circuit Breaker Tripped! PNR Service is unreachable. Reason: {}", t.getMessage());

        // Throw a clean, user-friendly exception instead of a Feign Connection Refused error
        throw new RuntimeException("Booking engine is currently degraded. The PNR generator is unreachable. Please try again in 2 minutes.");
    }

    @Transactional(readOnly = true)
    public ReservationRes getReservationByPnr(String pnr) {
        Reservation reservation = reservationRepository.findByPnr(pnr)
                .orElseThrow(() -> new RuntimeException("Reservation not found for PNR: " + pnr));
        return mapToDto(reservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationRes> getMyBookings(String username) {
        return reservationRepository.findByUsername(username)
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    private ReservationRes mapToDto(Reservation res) {
        List<PassengerDto> pDtos = res.getPassengers().stream().map(p -> PassengerDto.builder()
                .name(p.getName())
                .age(p.getAge())
                .gender(p.getGender())
                .birthPreference(p.getBirthPreference())
                .seatNumber(p.getSeatNumber())
                .bookingStatus(p.getBookingStatus().name())
                .build()).collect(Collectors.toList());

        return ReservationRes.builder()
                .pnr(res.getPnr())
                .trainNumber(res.getTrainNumber())
                .dateOfJourney(res.getDateOfJourney())
                .bookingTime(res.getBookingTime())
                .status(res.getStatus().name())
                .passengers(pDtos)
                .build();
    }

    @Transactional
    public CancellationResponse cancelReservation(String pnr) {
        // 1. Fetch the ticket
        Reservation reservation = reservationRepository.findByPnr(pnr)
                .orElseThrow(() -> new RuntimeException("PNR not found: " + pnr));

        // 2. Idempotency Check: Ensure we don't refund an already cancelled ticket
        if (reservation.getStatus() == Reservation.ReservationStatus.CANCELLED) {
            throw new RuntimeException("Ticket with PNR " + pnr + " is already cancelled.");
        }

        // 3. Financial Calculation (e.g., 20% cancellation charge)
        double originalFare = reservation.getTotalFare();
        double cancellationFee = originalFare * 0.20;
        double refundAmount = originalFare - cancellationFee;

        // 4. Soft Delete (Update status instead of deleting the row)
        reservation.setStatus(Reservation.ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);

        // 5. Build and return the receipt
        return CancellationResponse.builder()
                .pnr(pnr)
                .message("Ticket cancelled successfully. Refund will be processed in 3-5 business days.")
                .originalFare(originalFare)
                .cancellationFee(cancellationFee)
                .refundAmount(refundAmount)
                .currentStatus(reservation.getStatus().name())
                .build();
    }
}