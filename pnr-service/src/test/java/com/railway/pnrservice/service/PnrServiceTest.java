package com.railway.pnrservice.service;

import com.railway.pnrservice.entity.PnrRecord;
import com.railway.pnrservice.repository.PnrRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PnrServiceTest {

    @Mock
    private PnrRepository pnrRepository;

    @InjectMocks
    private PnrService pnrService;

    @Test
    void generateUniquePnr_SuccessOnFirstTry() {
        // Arrange
        when(pnrRepository.existsByPnrNumber(anyString())).thenReturn(false);
        when(pnrRepository.save(any(PnrRecord.class))).thenReturn(new PnrRecord());

        // Act
        String pnr = pnrService.generateUniquePnr();

        // Assert
        assertNotNull(pnr);
        assertEquals(10, pnr.length());
        verify(pnrRepository, times(1)).existsByPnrNumber(anyString());
        verify(pnrRepository, times(1)).save(any(PnrRecord.class));
    }

    @Test
    void generateUniquePnr_WithCollision_ShouldRetryUntilUnique() {
        // Arrange: 1st call returns true (collision), 2nd call returns false (success)
        when(pnrRepository.existsByPnrNumber(anyString()))
                .thenReturn(true)
                .thenReturn(false);

        when(pnrRepository.save(any(PnrRecord.class))).thenReturn(new PnrRecord());

        // Act
        String pnr = pnrService.generateUniquePnr();

        // Assert
        assertNotNull(pnr);
        assertEquals(10, pnr.length());
        verify(pnrRepository, times(2)).existsByPnrNumber(anyString());
        verify(pnrRepository, times(1)).save(any(PnrRecord.class));
    }
}