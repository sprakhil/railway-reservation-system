package com.railway.reservationservice.dto;

import java.util.List;

/** One train with a single-passenger fare and current availability for each configured class. */
public record TrainSearchResult(String trainNumber, String trainName, List<JourneyQuote> classes) {}
