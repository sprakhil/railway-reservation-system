package com.railway.userservice.service;

import com.railway.userservice.dto.UserProfileDto;
import com.railway.userservice.entity.UserProfile;
import com.railway.userservice.entity.UserProfile.Gender;
import com.railway.userservice.exception.ResourceNotFoundException;
import com.railway.userservice.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository profileRepository;

    @InjectMocks
    private UserProfileService profileService;

    @Test
    void getProfileByUsername_WhenExists_ReturnsDto() {
        // 1. Arrange
        String username = "johndoe";
        UserProfile mockProfile = new UserProfile();
        mockProfile.setUsername(username);
        mockProfile.setFirstName("John");
        mockProfile.setLastName("Doe");
        mockProfile.setGender(Gender.MALE);
        mockProfile.setDateOfBirth(LocalDate.of(1990, 5, 15));

        when(profileRepository.findByUsername(username)).thenReturn(Optional.of(mockProfile));

        // 2. Act
        UserProfileDto result = profileService.getProfileByUsername(username);

        // 3. Assert
        assertNotNull(result);
        assertEquals("John", result.getFirstName());
        assertEquals("Doe", result.getLastName());
        assertEquals(Gender.MALE, result.getGender());
        assertEquals(LocalDate.of(1990, 5, 15), result.getDateOfBirth());
    }

    @Test
    void getProfileByUsername_WhenNotFound_ThrowsException() {
        // Arrange
        String username = "unknown_user";
        when(profileRepository.findByUsername(username)).thenReturn(Optional.empty());

        // Act & Assert
        Exception exception = assertThrows(ResourceNotFoundException.class, () -> {
            profileService.getProfileByUsername(username);
        });

        assertTrue(exception.getMessage().contains("Profile not found"));
    }

    @Test
    void createOrUpdateProfile_WhenProfileDoesNotExist_CreatesNew() {
        // Arrange
        String username = "newuser";
        UserProfileDto inputDto = new UserProfileDto();
        inputDto.setFirstName("New");
        inputDto.setPhoneNumber("1234567890");

        // Mock finding nothing (triggers the "Create" flow)
        when(profileRepository.findByUsername(username)).thenReturn(Optional.empty());

        // Mock the save operation to just return whatever entity was passed to it
        when(profileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserProfileDto result = profileService.createOrUpdateProfile(username, inputDto);

        // Assert
        assertNotNull(result);
        assertEquals(username, result.getUsername());
        assertEquals("New", result.getFirstName());
        assertEquals("1234567890", result.getPhoneNumber());

        // Verify save was called exactly once
        verify(profileRepository, times(1)).save(any(UserProfile.class));
    }

    @Test
    void createOrUpdateProfile_WhenProfileExists_UpdatesExisting() {
        // Arrange
        String username = "existinguser";

        // The existing profile in the database
        UserProfile existingProfile = new UserProfile();
        existingProfile.setUsername(username);
        existingProfile.setFirstName("OldName");
        existingProfile.setAddress("Old Address");

        // The incoming DTO with new data
        UserProfileDto inputDto = new UserProfileDto();
        inputDto.setFirstName("UpdatedName");
        inputDto.setAddress("New Address");

        // Mock finding the existing profile (triggers the "Update" flow)
        when(profileRepository.findByUsername(username)).thenReturn(Optional.of(existingProfile));
        when(profileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserProfileDto result = profileService.createOrUpdateProfile(username, inputDto);

        // Assert
        assertNotNull(result);
        assertEquals("UpdatedName", result.getFirstName()); // Ensure the name was actually updated
        assertEquals("New Address", result.getAddress());

        verify(profileRepository, times(1)).save(any(UserProfile.class));
    }
}