package com.railway.userservice.controller;

import com.railway.userservice.dto.UserProfileDto;
import com.railway.userservice.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileControllerTest {

    @Mock
    private UserProfileService profileService;

    // We mock the Spring Security Authentication object!
    @Mock
    private Authentication authentication;

    @InjectMocks
    private UserProfileController userProfileController;

    @Test
    void getMyProfile_ShouldReturn200AndProfile() {
        // Arrange
        String loggedInUser = "johndoe";
        UserProfileDto mockDto = new UserProfileDto();
        mockDto.setUsername(loggedInUser);
        mockDto.setFirstName("John");

        // Tell the mock authentication object to return our fake username
        when(authentication.getName()).thenReturn(loggedInUser);
        when(profileService.getProfileByUsername(loggedInUser)).thenReturn(mockDto);

        // Act
        ResponseEntity<UserProfileDto> response = userProfileController.getMyProfile(authentication);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("John", response.getBody().getFirstName());
    }

    @Test
    void updateMyProfile_ShouldReturn200AndUpdatedProfile() {
        // Arrange
        String loggedInUser = "johndoe";
        UserProfileDto inputDto = new UserProfileDto();
        inputDto.setFirstName("Johnny");

        UserProfileDto outputDto = new UserProfileDto();
        outputDto.setUsername(loggedInUser);
        outputDto.setFirstName("Johnny");

        when(authentication.getName()).thenReturn(loggedInUser);
        when(profileService.createOrUpdateProfile(eq(loggedInUser), any(UserProfileDto.class))).thenReturn(outputDto);

        // Act
        ResponseEntity<UserProfileDto> response = userProfileController.updateMyProfile(inputDto, authentication);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Johnny", response.getBody().getFirstName());
    }

    @Test
    void getUserProfileByAdmin_ShouldReturn200() {
        // Arrange
        String targetUser = "janedoe";
        UserProfileDto mockDto = new UserProfileDto();
        mockDto.setUsername(targetUser);

        when(profileService.getProfileByUsername(targetUser)).thenReturn(mockDto);

        // Act - No authentication object needed for this specific method call in our test
        ResponseEntity<UserProfileDto> response = userProfileController.getUserProfileByAdmin(targetUser);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(targetUser, response.getBody().getUsername());
    }
}