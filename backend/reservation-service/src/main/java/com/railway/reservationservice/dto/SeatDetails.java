package com.railway.reservationservice.dto;

/** Display metadata derived by the server; the persisted seatNumber remains the inventory key. */
public record SeatDetails(String coach, Integer position, String berthType, String berthLabel,
                          String description, String layout, Integer bay) {}
