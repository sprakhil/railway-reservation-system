package com.railway.reservationservice.dto;
import java.util.List;
import java.math.BigDecimal;
public record JourneyPlan(String id,String kind,List<JourneyQuote> legs,String interchangeStation,
    long transferMinutes,long durationMinutes,BigDecimal totalFare,String allocation,
    double originDistanceKm,double destinationDistanceKm) {
    public record Results(List<JourneyPlan> options,List<String> notices,boolean limited,
        int radiusKm,int minTransferMinutes,int maxTransferMinutes) {}
}
