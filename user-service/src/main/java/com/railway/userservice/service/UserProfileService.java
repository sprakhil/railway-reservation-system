package com.railway.userservice.service;

import com.railway.userservice.dto.UserProfileDto;
import com.railway.userservice.entity.UserProfile;
import com.railway.userservice.exception.ResourceNotFoundException;
import com.railway.userservice.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private final UserProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public UserProfileDto getProfileByUsername(String username) {
        UserProfile profile = profileRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found for user: " + username));

        return mapToDto(profile);
    }

    @Transactional
    public UserProfileDto createOrUpdateProfile(String username, UserProfileDto dto) {
        UserProfile profile = profileRepository.findByUsername(username)
                .orElse(new UserProfile()); // Create new if it doesn't exist

        profile.setUsername(username);
        profile.setFirstName(dto.getFirstName());
        profile.setLastName(dto.getLastName());
        profile.setPhoneNumber(dto.getPhoneNumber());
        profile.setGender(dto.getGender());
        profile.setDateOfBirth(dto.getDateOfBirth());
        profile.setAddress(dto.getAddress());

        UserProfile savedProfile = profileRepository.save(profile);
        log.info("Profile updated successfully for user: {}", username);

        return mapToDto(savedProfile);
    }

    private UserProfileDto mapToDto(UserProfile profile) {
        return UserProfileDto.builder()
                .username(profile.getUsername())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .phoneNumber(profile.getPhoneNumber())
                .gender(profile.getGender())
                .dateOfBirth(profile.getDateOfBirth())
                .address(profile.getAddress())
                .build();
    }
}