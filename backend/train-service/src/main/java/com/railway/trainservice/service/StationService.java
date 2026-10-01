package com.railway.trainservice.service;

import com.railway.trainservice.dto.StationDto;
import com.railway.trainservice.entity.Station;
import com.railway.trainservice.exception.ResourceNotFoundException;
import com.railway.trainservice.exception.CatalogConflictException;
import com.railway.trainservice.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StationService {

    private final StationRepository stationRepository;

    public StationDto addStation(StationDto dto) {
        dto.setStationCode(dto.getStationCode());
        if (stationRepository.existsByStationCode(dto.getStationCode())) {
            throw new CatalogConflictException("Station code already exists");
        }
        Station station = Station.builder()
                .stationCode(dto.getStationCode())
                .stationName(dto.getStationName())
                .city(dto.getCity())
                .state(dto.getState())
                .zone(dto.getZone())
                .latitude(dto.getLatitude()).longitude(dto.getLongitude())
                .build();
        return mapToDto(stationRepository.save(station));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public com.railway.trainservice.dto.PageResult<StationDto> page(int page,int size,String sort,String direction) {
        return com.railway.trainservice.dto.PageResult.of(stationRepository.findAll(com.railway.trainservice.config.Paging.of(page,size,sort,direction,
            java.util.Set.of("stationId","stationName","stationCode"),"stationId")).map(this::mapToDto));
    }
    public List<StationDto> getAllStations() {
        return stationRepository.findAll().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public StationDto getStationById(Long id) {
        return mapToDto(stationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found")));
    }

    public StationDto getStationByCode(String code) {
        String normalized = code == null ? null : code.strip().toUpperCase(java.util.Locale.ROOT);
        return mapToDto(stationRepository.findByStationCode(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found")));
    }

    @Transactional
    public StationDto updateStation(Long id, StationDto dto) {
        dto.setStationCode(dto.getStationCode());
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found"));
        if (!station.getStationCode().equals(dto.getStationCode()))
            throw new CatalogConflictException("Station codes are referenced by timetables and cannot be changed");
        station.setStationName(dto.getStationName());
        station.setCity(dto.getCity()); station.setState(dto.getState()); station.setZone(dto.getZone());
        station.setLatitude(dto.getLatitude()); station.setLongitude(dto.getLongitude());
        return mapToDto(stationRepository.save(station));
    }

    private StationDto mapToDto(Station station) {
        return StationDto.builder()
                .stationId(station.getStationId())
                .stationCode(station.getStationCode())
                .stationName(station.getStationName())
                .city(station.getCity())
                .state(station.getState())
                .zone(station.getZone())
                .latitude(station.getLatitude()).longitude(station.getLongitude())
                .build();
    }
}
