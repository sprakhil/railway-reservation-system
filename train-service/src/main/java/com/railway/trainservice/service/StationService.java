package com.railway.trainservice.service;

import com.railway.trainservice.dto.StationDto;
import com.railway.trainservice.entity.Station;
import com.railway.trainservice.exception.ResourceNotFoundException;
import com.railway.trainservice.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StationService {

    private final StationRepository stationRepository;

    public StationDto addStation(StationDto dto) {
        if (stationRepository.existsByStationCode(dto.getStationCode())) {
            throw new RuntimeException("Station code already exists");
        }
        Station station = Station.builder()
                .stationCode(dto.getStationCode())
                .stationName(dto.getStationName())
                .city(dto.getCity())
                .state(dto.getState())
                .zone(dto.getZone())
                .build();
        return mapToDto(stationRepository.save(station));
    }

    public List<StationDto> getAllStations() {
        return stationRepository.findAll().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public StationDto getStationById(Long id) {
        return mapToDto(stationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found")));
    }

    private StationDto mapToDto(Station station) {
        return StationDto.builder()
                .stationId(station.getStationId())
                .stationCode(station.getStationCode())
                .stationName(station.getStationName())
                .city(station.getCity())
                .state(station.getState())
                .zone(station.getZone())
                .build();
    }
}