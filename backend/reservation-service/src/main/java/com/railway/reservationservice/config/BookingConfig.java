package com.railway.reservationservice.config;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class BookingConfig { @Bean public Clock bookingClock() { return Clock.systemUTC(); } }
