package com.railway.userservice.controller;

import com.railway.userservice.service.UserProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(ProfileAccessTest.TestConfig.class)
class ProfileAccessTest {
    @Configuration @EnableMethodSecurity
    static class TestConfig {
        @Bean UserProfileService profiles() { return mock(UserProfileService.class); }
        @Bean com.railway.userservice.service.AuthAccountClient accounts() { return mock(com.railway.userservice.service.AuthAccountClient.class); }
        @Bean UserProfileController controller(UserProfileService profiles,com.railway.userservice.service.AuthAccountClient accounts) { return new UserProfileController(profiles,accounts); }
    }
    @Autowired UserProfileController controller;
    @Autowired UserProfileService profiles;
    @Autowired com.railway.userservice.service.AuthAccountClient accounts;
    @AfterEach void clear() { SecurityContextHolder.clearContext(); reset(profiles,accounts); }
    private void login(String name, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(name, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
    @Test void passengerCannotReadAnotherProfile() {
        login("alice", "PASSENGER");
        assertThrows(AccessDeniedException.class, () -> controller.getUserProfileByAdmin("bob"));
        verifyNoInteractions(profiles);
    }
    @Test void passengerCanReadOwnProfile() {
        login("alice", "PASSENGER"); controller.getUserProfileByAdmin("alice");
        verify(profiles).getProfileByUsername("alice");
    }
    @Test void adminCanReadAnotherProfile() {
        when(accounts.canonicalUsername("bob")).thenReturn(java.util.Optional.of("bob"));
        login("admin", "ADMIN"); controller.getUserProfileByAdmin("bob");
        verify(profiles).getProfileByUsername("bob");
    }
    @Test void passengerCanReadOwnChosenUsernameWithoutChangingOwnership() {
        when(accounts.canonicalUsername("new_alice")).thenReturn(java.util.Optional.of("alice"));
        login("alice","PASSENGER");controller.getUserProfileByAdmin("new_alice");
        verify(profiles).getProfileByUsername("alice");
    }
}
