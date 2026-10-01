package com.railway.pnrservice.controller;

import com.railway.pnrservice.service.PnrService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PnrControllerTest {

    @Mock
    private PnrService pnrService;

    @InjectMocks
    private PnrController pnrController;

    @Test
    void generatePnr_ShouldReturn200And10DigitString() {
        // Arrange
        String expectedPnr = "1234567890";
        when(pnrService.generateUniquePnr()).thenReturn(expectedPnr);

        // Act
        ResponseEntity<String> response = pnrController.generatePnr();

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("1234567890", response.getBody());
    }
}